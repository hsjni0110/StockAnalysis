"""
XBRL Normalization Service
FastAPI service for normalizing XBRL filings using Arelle and FAC mapping
"""
import logging
import time
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from .models import (
    NormalizationRequest,
    NormalizationResponse,
    ValidationRequest,
    ValidationResponse,
    HealthResponse,
    NormalizedConcept,
    ValidationIssue
)
from .arelle_processor import ArelleProcessor, ARELLE_AVAILABLE
from .fac_mapper import FacMapper

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Global instances
arelle_processor: Optional[ArelleProcessor] = None
fac_mapper: Optional[FacMapper] = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifecycle handler for startup and shutdown"""
    global arelle_processor, fac_mapper

    # Startup
    logger.info("Starting XBRL Normalization Service")

    if not ARELLE_AVAILABLE:
        logger.error("Arelle is not available - service will not function properly")
    else:
        try:
            arelle_processor = ArelleProcessor()
            fac_mapper = FacMapper()
            logger.info("Arelle processor and FAC mapper initialized successfully")
        except Exception as e:
            logger.error(f"Failed to initialize services: {str(e)}", exc_info=True)

    yield

    # Shutdown
    logger.info("Shutting down XBRL Normalization Service")
    if arelle_processor:
        arelle_processor.close()


# Create FastAPI app
app = FastAPI(
    title="XBRL Normalization Service",
    description="Service for normalizing SEC XBRL filings using Arelle and FAC concepts",
    version="1.0.0",
    lifespan=lifespan
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure appropriately for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health", response_model=HealthResponse)
async def health_check():
    """Health check endpoint"""
    arelle_version = None

    if ARELLE_AVAILABLE:
        try:
            from arelle import Version
            arelle_version = getattr(Version, '__version__', 'unknown')
        except Exception:
            pass

    return HealthResponse(
        status="healthy" if arelle_processor is not None else "degraded",
        arelle_version=arelle_version
    )


@app.post("/normalize", response_model=NormalizationResponse)
async def normalize_filing(request: NormalizationRequest):
    """
    Normalize an XBRL filing to FAC concepts

    Args:
        request: Normalization request with filing URL and CIK

    Returns:
        Normalized financial concepts
    """
    if not arelle_processor or not fac_mapper:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Normalization service is not available (Arelle not initialized)"
        )

    start_time = time.time()

    try:
        logger.info(f"Normalizing filing: {request.filing_url} (CIK: {request.cik})")

        # Load XBRL instance
        model_xbrl = arelle_processor.load_instance(request.filing_url)

        if model_xbrl is None:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Failed to load XBRL instance from {request.filing_url}"
            )

        # Extract facts
        facts = arelle_processor.extract_facts(model_xbrl)

        # Map to FAC concepts
        normalized_concepts = []
        concept_counts = {}

        for fact in facts:
            xbrl_concept = fact.get('concept')
            namespace = fact.get('namespace')

            # Map to FAC
            fac_concept = fac_mapper.map_to_fac(xbrl_concept, namespace)

            if fac_concept:
                # Calculate quality score
                confidence = fac_mapper.get_confidence_score(xbrl_concept, fac_concept)

                # Create normalized concept
                normalized = NormalizedConcept(
                    concept=fac_concept,
                    value=fact.get('value'),
                    period_type=fact.get('period_type', 'unknown'),
                    context_ref=fact.get('context_ref'),
                    unit=fact.get('unit'),
                    start_date=fact.get('start_date'),
                    end_date=fact.get('end_date'),
                    quality_score=confidence,
                    source='arelle-fac'
                )

                normalized_concepts.append(normalized)
                concept_counts[fac_concept] = concept_counts.get(fac_concept, 0) + 1

        processing_time = int((time.time() - start_time) * 1000)

        logger.info(f"Normalized {len(normalized_concepts)} concepts from {len(facts)} facts in {processing_time}ms")

        return NormalizationResponse(
            filing_url=request.filing_url,
            cik=request.cik,
            concepts=normalized_concepts,
            metadata={
                "total_facts": len(facts),
                "normalized_concepts": len(normalized_concepts),
                "unique_concepts": len(concept_counts),
                "concept_breakdown": concept_counts
            },
            processing_time_ms=processing_time,
            concept_count=len(normalized_concepts)
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error normalizing filing: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Internal error during normalization: {str(e)}"
        )


@app.post("/validate", response_model=ValidationResponse)
async def validate_filing(request: ValidationRequest):
    """
    Validate an XBRL filing using DQC rules

    Note: Full DQC validation requires additional plugins.
    This is a placeholder implementation that performs basic validation.

    Args:
        request: Validation request with filing URL

    Returns:
        Validation results
    """
    if not arelle_processor:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Validation service is not available (Arelle not initialized)"
        )

    start_time = time.time()

    try:
        logger.info(f"Validating filing: {request.filing_url}")

        # Load XBRL instance
        model_xbrl = arelle_processor.load_instance(request.filing_url)

        if model_xbrl is None:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Failed to load XBRL instance from {request.filing_url}"
            )

        errors = []
        warnings = []
        info_issues = []

        # Basic validation from Arelle's built-in checks
        if hasattr(model_xbrl, 'errors') and model_xbrl.errors:
            for error in model_xbrl.errors:
                issue = ValidationIssue(
                    rule_id="ARELLE_ERROR",
                    severity="error",
                    message=str(error),
                    affected_concept=None
                )
                errors.append(issue)

        # Check for common issues
        if len(model_xbrl.facts) == 0:
            warnings.append(ValidationIssue(
                rule_id="DQC_CUSTOM_001",
                severity="warning",
                message="No facts found in XBRL instance",
                affected_concept=None
            ))

        # Check balance sheet equation (Assets = Liabilities + Equity)
        # This is a simplified version - full DQC rules are more complex
        assets_facts = [f for f in model_xbrl.facts if 'Assets' in str(f.qname)]
        liabilities_facts = [f for f in model_xbrl.facts if 'Liabilities' in str(f.qname)]

        if assets_facts and not liabilities_facts:
            warnings.append(ValidationIssue(
                rule_id="DQC_CUSTOM_002",
                severity="warning",
                message="Assets reported but Liabilities missing",
                affected_concept="Assets/Liabilities"
            ))

        processing_time = int((time.time() - start_time) * 1000)

        logger.info(f"Validation complete: {len(errors)} errors, {len(warnings)} warnings in {processing_time}ms")

        return ValidationResponse(
            filing_url=request.filing_url,
            errors=errors,
            warnings=warnings,
            info=info_issues,
            error_count=len(errors),
            warning_count=len(warnings),
            info_count=len(info_issues),
            processing_time_ms=processing_time
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error validating filing: {str(e)}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Internal error during validation: {str(e)}"
        )


@app.get("/concepts", response_model=dict)
async def list_supported_concepts():
    """List all supported FAC concepts"""
    if not fac_mapper:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="FAC mapper is not available"
        )

    concepts = fac_mapper.get_supported_concepts()

    return {
        "concepts": concepts,
        "count": len(concepts),
        "description": "Fundamental Accounting Concepts (FAC) supported by this service"
    }


@app.get("/concepts/{concept}/patterns", response_model=dict)
async def get_concept_patterns(concept: str):
    """Get pattern information for a specific FAC concept"""
    if not fac_mapper:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="FAC mapper is not available"
        )

    patterns = fac_mapper.get_pattern_info(concept)

    if patterns is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Concept '{concept}' not found"
        )

    return {
        "concept": concept,
        "patterns": patterns,
        "pattern_count": len(patterns)
    }


@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    """Global exception handler"""
    logger.error(f"Unhandled exception: {str(exc)}", exc_info=True)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"detail": "Internal server error"}
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5000, log_level="info")
