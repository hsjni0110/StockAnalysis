"""
Arelle XBRL processor wrapper
Handles loading and processing XBRL instance documents using Arelle
"""
import logging
from typing import Optional, List, Dict, Any
from datetime import datetime, date
import os

logger = logging.getLogger(__name__)

# Lazy import Arelle to avoid import errors if not installed
try:
    from arelle import Cntlr
    from arelle.ModelXbrl import ModelXbrl
    from arelle import ModelValue
    ARELLE_AVAILABLE = True
except ImportError:
    logger.warning("Arelle not available - install with 'pip install arelle-release'")
    ARELLE_AVAILABLE = False


class ArelleProcessor:
    """
    Wrapper for Arelle XBRL processor
    Provides methods to load and extract data from XBRL instances
    """

    def __init__(self):
        if not ARELLE_AVAILABLE:
            raise RuntimeError("Arelle is not installed. Install with 'pip install arelle-release'")

        self.controller = Cntlr.Cntlr(logFileName="logToBuffer")
        self.controller.webCache.workOffline = False

        # Set User-Agent for SEC compliance
        user_agent = os.getenv('SEC_USER_AGENT', 'StockDeltaSystem/1.0; admin@stockdelta.com')
        self.controller.webCache.opener.addheaders = [('User-Agent', user_agent)]

        # Enable SEC inline XBRL transformations
        # This suppresses "unrecognized transformation namespace" warnings
        try:
            from arelle.plugin import inlineXbrlDocumentSet
            # Load plugins for SEC filings
            self.controller.modelManager.pluginConfig.enable('validate/EFM')
            self.controller.modelManager.pluginConfig.enable('EdgarRenderer')
        except Exception as e:
            logger.warning(f"Could not load SEC plugins: {e}. Transformations may show warnings.")

        logger.info("Arelle processor initialized")

    def load_instance(self, filing_url: str) -> Optional[ModelXbrl]:
        """
        Load an XBRL instance from a URL

        Args:
            filing_url: URL to the XBRL instance document

        Returns:
            ModelXbrl object or None if loading failed
        """
        try:
            logger.info(f"Loading XBRL instance: {filing_url}")

            # Suppress transformation namespace warnings during load
            import logging
            arelle_logger = logging.getLogger('arelle')
            original_level = arelle_logger.level
            arelle_logger.setLevel(logging.CRITICAL)

            model_xbrl = self.controller.modelManager.load(filing_url)

            # Restore original log level
            arelle_logger.setLevel(original_level)

            if model_xbrl is None:
                logger.error(f"Failed to load XBRL instance: {filing_url}")
                return None

            if model_xbrl.errors:
                logger.warning(f"XBRL instance loaded with {len(model_xbrl.errors)} errors")

            logger.info(f"Successfully loaded XBRL instance with {len(model_xbrl.facts)} facts")
            return model_xbrl

        except Exception as e:
            logger.error(f"Error loading XBRL instance: {str(e)}", exc_info=True)
            return None

    def extract_facts(self, model_xbrl: ModelXbrl) -> List[Dict[str, Any]]:
        """
        Extract all facts from an XBRL instance

        Args:
            model_xbrl: Loaded ModelXbrl object

        Returns:
            List of fact dictionaries
        """
        facts = []

        for fact in model_xbrl.facts:
            try:
                fact_data = {
                    'concept': fact.qname.localName if fact.qname else None,
                    'namespace': fact.qname.namespaceURI if fact.qname else None,
                    'value': self._extract_fact_value(fact),
                    'unit': self._extract_unit(fact),
                    'decimals': fact.decimals,
                    'context_ref': fact.contextID,
                    'period_type': self._get_period_type(fact),
                    'start_date': self._get_period_start(fact),
                    'end_date': self._get_period_end(fact),
                }

                facts.append(fact_data)

            except Exception as e:
                logger.debug(f"Error extracting fact {fact.qname}: {str(e)}")
                continue

        logger.info(f"Extracted {len(facts)} facts from XBRL instance")
        return facts

    def _extract_fact_value(self, fact) -> Optional[float]:
        """Extract numeric value from a fact"""
        try:
            if fact.xValue is not None:
                if isinstance(fact.xValue, (int, float)):
                    return float(fact.xValue)
                elif hasattr(fact.xValue, '__float__'):
                    return float(fact.xValue)

            # Try parsing from text content
            if fact.value:
                return float(fact.value.replace(',', ''))
        except (ValueError, AttributeError, TypeError):
            pass

        return None

    def _extract_unit(self, fact) -> Optional[str]:
        """Extract unit from a fact"""
        try:
            if fact.unit is not None:
                measures = fact.unit.measures
                if measures and len(measures) > 0:
                    # Get first measure (numerator)
                    measure_list = list(measures[0])
                    if measure_list:
                        qname = measure_list[0]
                        return qname.localName if hasattr(qname, 'localName') else str(qname)
        except Exception:
            pass

        return None

    def _get_period_type(self, fact) -> str:
        """Determine if fact is instant or duration"""
        try:
            if fact.context:
                if fact.context.isInstantPeriod:
                    return 'instant'
                elif fact.context.isStartEndPeriod or fact.context.isForeverPeriod:
                    return 'duration'
        except Exception:
            pass

        return 'unknown'

    def _get_period_start(self, fact) -> Optional[str]:
        """Get period start date"""
        try:
            if fact.context and hasattr(fact.context, 'startDatetime'):
                dt = fact.context.startDatetime
                if dt:
                    return dt.date().isoformat() if hasattr(dt, 'date') else str(dt)
        except Exception:
            pass

        return None

    def _get_period_end(self, fact) -> Optional[str]:
        """Get period end date"""
        try:
            if fact.context:
                if hasattr(fact.context, 'endDatetime'):
                    dt = fact.context.endDatetime
                    if dt:
                        return dt.date().isoformat() if hasattr(dt, 'date') else str(dt)
                elif hasattr(fact.context, 'instantDatetime'):
                    dt = fact.context.instantDatetime
                    if dt:
                        return dt.date().isoformat() if hasattr(dt, 'date') else str(dt)
        except Exception:
            pass

        return None

    def close(self):
        """Clean up resources"""
        if self.controller:
            self.controller.close()
            logger.info("Arelle processor closed")
