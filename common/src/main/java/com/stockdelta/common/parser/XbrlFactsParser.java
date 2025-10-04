package com.stockdelta.common.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockdelta.common.entity.XbrlFact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Component
public class XbrlFactsParser {

    private static final Logger logger = LoggerFactory.getLogger(XbrlFactsParser.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Key GAAP tags we're interested in
    private static final Set<String> KEY_GAAP_TAGS = Set.of(
            "Revenues", "Revenue", "RevenueFromContractWithCustomerExcludingAssessedTax",
            "OperatingIncomeLoss", "NetIncomeLoss", "EarningsPerShareBasic", "EarningsPerShareDiluted",
            "InventoryNet", "TotalAssets", "TotalLiabilities", "StockholdersEquity",
            "CashAndCashEquivalents", "PropertyPlantAndEquipmentNet",
            "CapitalExpenditures", "OperatingCashFlow"
    );

    public List<XbrlFact> parseCompanyFacts(String response, Long filingId) throws Exception {
        List<XbrlFact> facts = new ArrayList<>();
        JsonNode root = objectMapper.readTree(response);

        // Parse US-GAAP facts
        JsonNode usGaapFacts = root.path("facts").path("us-gaap");
        if (!usGaapFacts.isMissingNode()) {
            facts.addAll(parseFactsForTaxonomy(usGaapFacts, "us-gaap", filingId));
        }

        // Parse DEI (Document Entity Information) facts
        JsonNode deiFacts = root.path("facts").path("dei");
        if (!deiFacts.isMissingNode()) {
            facts.addAll(parseFactsForTaxonomy(deiFacts, "dei", filingId));
        }

        if (filingId != null) {
            logger.info("Parsed {} XBRL facts for filing ID: {}", facts.size(), filingId);
        } else {
            logger.info("Parsed {} XBRL facts from company facts", facts.size());
        }
        return facts;
    }

    private List<XbrlFact> parseFactsForTaxonomy(JsonNode taxonomyNode, String taxonomy, Long filingId) {
        List<XbrlFact> facts = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        Iterator<String> tagNames = taxonomyNode.fieldNames();
        while (tagNames.hasNext()) {
            String tag = tagNames.next();

            // For us-gaap, only process key tags to reduce noise
            if ("us-gaap".equals(taxonomy) && !KEY_GAAP_TAGS.contains(tag)) {
                continue;
            }

            JsonNode tagData = taxonomyNode.get(tag);
            JsonNode units = tagData.path("units");

            if (units.isMissingNode()) {
                continue;
            }

            // Process each unit (USD, shares, etc.)
            Iterator<String> unitNames = units.fieldNames();
            while (unitNames.hasNext()) {
                String unit = unitNames.next();
                JsonNode unitData = units.get(unit);

                if (unitData.isArray()) {
                    for (JsonNode factNode : unitData) {
                        try {
                            XbrlFact fact = parseFactNode(factNode, taxonomy, tag, unit, filingId, dateFormatter);
                            if (fact != null) {
                                facts.add(fact);
                            }
                        } catch (Exception e) {
                            logger.debug("Failed to parse fact node for tag {}: {}", tag, e.getMessage());
                        }
                    }
                }
            }
        }

        return facts;
    }

    private XbrlFact parseFactNode(JsonNode factNode, String taxonomy, String tag, String unit,
                                  Long filingId, DateTimeFormatter dateFormatter) {
        try {
            XbrlFact fact = new XbrlFact();
            fact.setFilingId(filingId);
            fact.setTaxonomy(taxonomy);
            fact.setTag(tag);
            fact.setUnit(unit);

            // Parse value
            JsonNode valueNode = factNode.path("val");
            if (!valueNode.isMissingNode()) {
                fact.setValue(new BigDecimal(valueNode.asText()));
            }

            // Parse dates
            String startStr = factNode.path("start").asText(null);
            String endStr = factNode.path("end").asText(null);

            if (startStr != null && !startStr.isEmpty()) {
                fact.setStartDate(LocalDate.parse(startStr, dateFormatter));
            }

            if (endStr != null && !endStr.isEmpty()) {
                fact.setEndDate(LocalDate.parse(endStr, dateFormatter));
            }

            // Parse additional fields
            JsonNode scaleNode = factNode.path("scale");
            if (!scaleNode.isMissingNode()) {
                fact.setScale(scaleNode.asInt(0));
            }

            JsonNode decimalsNode = factNode.path("decimals");
            if (!decimalsNode.isMissingNode()) {
                fact.setDecimals(decimalsNode.asInt());
            }

            // Store frame/form information in dimensions
            String frame = factNode.path("frame").asText(null);
            String form = factNode.path("form").asText(null);
            if (frame != null || form != null) {
                StringBuilder dims = new StringBuilder("{");
                if (frame != null) {
                    dims.append("\"frame\":\"").append(frame).append("\"");
                }
                if (form != null) {
                    if (frame != null) dims.append(",");
                    dims.append("\"form\":\"").append(form).append("\"");
                }
                dims.append("}");
                fact.setDimensions(dims.toString());
            }

            return fact;

        } catch (Exception e) {
            logger.debug("Failed to parse fact node: {}", e.getMessage());
            return null;
        }
    }

    public List<XbrlFact> filterByTag(List<XbrlFact> facts, String tag) {
        return facts.stream()
                .filter(fact -> tag.equals(fact.getTag()))
                .toList();
    }

    public List<XbrlFact> filterByTags(List<XbrlFact> facts, Set<String> tags) {
        return facts.stream()
                .filter(fact -> tags.contains(fact.getTag()))
                .toList();
    }

    public List<XbrlFact> filterByDateRange(List<XbrlFact> facts, LocalDate startDate, LocalDate endDate) {
        return facts.stream()
                .filter(fact -> fact.getEndDate() != null &&
                              !fact.getEndDate().isBefore(startDate) &&
                              !fact.getEndDate().isAfter(endDate))
                .toList();
    }

    public List<XbrlFact> filterLatestForEachTag(List<XbrlFact> facts) {
        return facts.stream()
                .filter(fact -> fact.getEndDate() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        XbrlFact::getTag,
                        java.util.stream.Collectors.maxBy(
                                java.util.Comparator.comparing(XbrlFact::getEndDate)
                        )
                ))
                .values()
                .stream()
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();
    }
}