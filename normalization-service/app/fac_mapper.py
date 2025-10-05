"""
FAC (Fundamental Accounting Concepts) Mapper
Maps US-GAAP XBRL tags to standardized FAC concepts
"""
import logging
import re
from typing import Optional, Dict, List

logger = logging.getLogger(__name__)


class FacMapper:
    """
    Maps XBRL concepts to FAC (Fundamental Accounting Concepts)
    Uses pattern matching and predefined rules
    """

    # Core FAC concepts with their pattern rules
    FAC_PATTERNS = {
        'Revenue': [
            r'^Revenue',
            r'^Sales.*Revenue',
            r'.*RevenueFromContract.*',
            r'^SalesRevenue'
        ],
        'CostOfRevenue': [
            r'^CostOfRevenue',
            r'^CostOfGoodsSold',
            r'^CostOfSales'
        ],
        'GrossProfit': [
            r'^GrossProfit'
        ],
        'OperatingIncome': [
            r'^OperatingIncome',
            r'^IncomeLossFromContinuingOperationsBeforeIncomeTaxes'
        ],
        'NetIncome': [
            r'^NetIncome',
            r'^ProfitLoss$',
            r'.*NetIncomeLoss$'
        ],
        'Assets': [
            r'^Assets$',
            r'^TotalAssets$'
        ],
        'CurrentAssets': [
            r'^AssetsCurrent$'
        ],
        'NoncurrentAssets': [
            r'^AssetsNoncurrent$'
        ],
        'Liabilities': [
            r'^Liabilities$',
            r'^TotalLiabilities$'
        ],
        'CurrentLiabilities': [
            r'^LiabilitiesCurrent$'
        ],
        'NoncurrentLiabilities': [
            r'^LiabilitiesNoncurrent$'
        ],
        'Equity': [
            r'^StockholdersEquity',
            r'.*Equity$'
        ],
        'Cash': [
            r'^Cash$',
            r'^CashAndCashEquivalents.*',
            r'.*CashAndCashEquivalents$'
        ],
        'Inventory': [
            r'^Inventory',
            r'.*InventoryNet$'
        ],
        'PropertyPlantEquipment': [
            r'^PropertyPlantAndEquipmentNet',
            r'.*PropertyPlantAndEquipment.*'
        ],
        'IntangibleAssets': [
            r'^IntangibleAssets',
            r'.*Goodwill.*'
        ],
        'AccountsReceivable': [
            r'^AccountsReceivable',
            r'.*ReceivablesNet.*'
        ],
        'AccountsPayable': [
            r'^AccountsPayable'
        ],
        'Debt': [
            r'.*Debt.*',
            r'.*BorrowingsShortTerm.*',
            r'.*BorrowingsLongTerm.*'
        ],
        'CapitalExpenditures': [
            r'^PaymentsToAcquirePropertyPlantAndEquipment',
            r'^CapitalExpenditures'
        ],
        'OperatingCashFlow': [
            r'^NetCashProvidedByUsedInOperatingActivities',
            r'^OperatingCashFlow'
        ],
        'EPS': [
            r'^EarningsPerShareBasic$'
        ],
        'EPSDiluted': [
            r'^EarningsPerShareDiluted$'
        ],
    }

    def __init__(self):
        self._compiled_patterns = {}
        self._compile_patterns()
        logger.info(f"FAC Mapper initialized with {len(self.FAC_PATTERNS)} concept groups")

    def _compile_patterns(self):
        """Pre-compile regex patterns for performance"""
        for concept, patterns in self.FAC_PATTERNS.items():
            self._compiled_patterns[concept] = [
                re.compile(pattern, re.IGNORECASE)
                for pattern in patterns
            ]

    def map_to_fac(self, xbrl_concept: str, namespace: Optional[str] = None) -> Optional[str]:
        """
        Map an XBRL concept to a FAC concept

        Args:
            xbrl_concept: XBRL concept local name
            namespace: XBRL namespace (e.g., 'us-gaap')

        Returns:
            FAC concept name or None if no mapping found
        """
        if not xbrl_concept:
            return None

        # Only process us-gaap concepts
        if namespace and not ('us-gaap' in namespace.lower() or 'fasb' in namespace.lower()):
            logger.debug(f"Skipping non-US-GAAP concept: {xbrl_concept} (namespace: {namespace})")
            return None

        # Try pattern matching
        for fac_concept, patterns in self._compiled_patterns.items():
            for pattern in patterns:
                if pattern.match(xbrl_concept):
                    logger.debug(f"Mapped '{xbrl_concept}' -> '{fac_concept}' via pattern {pattern.pattern}")
                    return fac_concept

        # No mapping found
        logger.debug(f"No FAC mapping found for: {xbrl_concept}")
        return None

    def get_confidence_score(self, xbrl_concept: str, fac_concept: str) -> float:
        """
        Calculate confidence score for a mapping

        Args:
            xbrl_concept: Source XBRL concept
            fac_concept: Mapped FAC concept

        Returns:
            Confidence score from 0.0 to 1.0
        """
        if not xbrl_concept or not fac_concept:
            return 0.0

        # Get patterns for this FAC concept
        patterns = self._compiled_patterns.get(fac_concept, [])

        for pattern in patterns:
            if pattern.match(xbrl_concept):
                # Exact match patterns get higher confidence
                if pattern.pattern.startswith('^') and pattern.pattern.endswith('$'):
                    return 1.0
                # Prefix/suffix patterns
                elif pattern.pattern.startswith('^') or pattern.pattern.endswith('$'):
                    return 0.95
                # Contains patterns
                else:
                    return 0.85

        return 0.5  # Default low confidence

    def get_supported_concepts(self) -> List[str]:
        """Get list of all supported FAC concepts"""
        return list(self.FAC_PATTERNS.keys())

    def get_pattern_info(self, fac_concept: str) -> Optional[List[str]]:
        """Get pattern information for a FAC concept"""
        return self.FAC_PATTERNS.get(fac_concept)
