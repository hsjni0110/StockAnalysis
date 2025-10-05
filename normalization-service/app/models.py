"""
Pydantic models for XBRL normalization service
"""
from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field
from datetime import datetime


class NormalizationRequest(BaseModel):
    """Request model for normalizing a filing"""
    filing_url: str = Field(..., description="URL to the XBRL instance document")
    cik: str = Field(..., description="Company CIK number")
    accession_no: Optional[str] = Field(None, description="Filing accession number")


class NormalizedConcept(BaseModel):
    """A single normalized financial concept"""
    concept: str = Field(..., description="FAC fundamental concept name")
    value: Optional[float] = Field(None, description="Numeric value")
    period_type: str = Field(..., description="instant or duration")
    context_ref: Optional[str] = Field(None, description="XBRL context reference")
    unit: Optional[str] = Field(None, description="Unit of measurement (USD, shares, etc.)")
    start_date: Optional[str] = Field(None, description="Period start date (ISO format)")
    end_date: Optional[str] = Field(None, description="Period end date (ISO format)")
    quality_score: float = Field(1.0, ge=0.0, le=1.0, description="Quality score from 0.0 to 1.0")
    source: str = Field("arelle-xule", description="Source of normalization")


class NormalizationResponse(BaseModel):
    """Response model for normalization result"""
    filing_url: str
    cik: str
    concepts: List[NormalizedConcept]
    metadata: Dict[str, Any] = Field(default_factory=dict)
    processing_time_ms: Optional[int] = None
    concept_count: int = 0


class ValidationRequest(BaseModel):
    """Request model for DQC validation"""
    filing_url: str = Field(..., description="URL to the XBRL instance document")


class ValidationIssue(BaseModel):
    """A single DQC validation issue"""
    rule_id: str = Field(..., description="DQC rule identifier (e.g., DQC_0001)")
    severity: str = Field(..., description="error, warning, or info")
    message: str = Field(..., description="Human-readable error message")
    affected_concept: Optional[str] = Field(None, description="XBRL concept affected by this issue")
    line_number: Optional[int] = Field(None, description="Line number in source document")


class ValidationResponse(BaseModel):
    """Response model for DQC validation result"""
    filing_url: str
    errors: List[ValidationIssue] = Field(default_factory=list)
    warnings: List[ValidationIssue] = Field(default_factory=list)
    info: List[ValidationIssue] = Field(default_factory=list)
    error_count: int = 0
    warning_count: int = 0
    info_count: int = 0
    processing_time_ms: Optional[int] = None


class HealthResponse(BaseModel):
    """Health check response"""
    status: str = "healthy"
    arelle_version: Optional[str] = None
    timestamp: datetime = Field(default_factory=datetime.utcnow)
