package com.stockdelta.common.service;

import com.stockdelta.common.entity.Filing;
import com.stockdelta.common.entity.FilingDelta;
import com.stockdelta.common.entity.FilingSection;
import com.stockdelta.common.repository.FilingDeltaRepository;
import com.stockdelta.common.repository.FilingRepository;
import com.stockdelta.common.repository.FilingSectionRepository;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch.Diff;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Service for computing differences (deltas) between filing sections
 * Compares current filing with previous quarter/year filing of the same form type
 */
@Service
@Transactional
public class FilingDiffService {

    private static final Logger logger = LoggerFactory.getLogger(FilingDiffService.class);

    private static final int MIN_SNIPPET_LENGTH = 20;
    private static final int MAX_SNIPPET_LENGTH = 500;

    private final FilingRepository filingRepository;
    private final FilingSectionRepository sectionRepository;
    private final FilingDeltaRepository deltaRepository;
    private final FilingSectionExtractor sectionExtractor;

    @Autowired
    public FilingDiffService(FilingRepository filingRepository,
                             FilingSectionRepository sectionRepository,
                             FilingDeltaRepository deltaRepository,
                             FilingSectionExtractor sectionExtractor) {
        this.filingRepository = filingRepository;
        this.sectionRepository = sectionRepository;
        this.deltaRepository = deltaRepository;
        this.sectionExtractor = sectionExtractor;
    }

    /**
     * Compute deltas for a filing by comparing with previous filing
     */
    public List<FilingDelta> computeDeltas(Long filingId) {
        Optional<Filing> currentFilingOpt = filingRepository.findById(filingId);
        if (currentFilingOpt.isEmpty()) {
            logger.warn("Filing not found: {}", filingId);
            return new ArrayList<>();
        }

        Filing currentFiling = currentFilingOpt.get();

        // Find previous filing of the same form type
        Optional<Filing> previousFilingOpt = findPreviousFiling(currentFiling);
        if (previousFilingOpt.isEmpty()) {
            logger.info("No previous filing found for comparison: {}", currentFiling.getAccessionNo());
            return new ArrayList<>();
        }

        Filing previousFiling = previousFilingOpt.get();
        logger.info("Comparing {} vs {} for CIK {}",
                currentFiling.getAccessionNo(), previousFiling.getAccessionNo(), currentFiling.getCik());

        // Get sections for both filings - extract if not present
        List<FilingSection> currentSections = getOrExtractSections(filingId);
        List<FilingSection> previousSections = getOrExtractSections(previousFiling.getId());

        List<FilingDelta> deltas = new ArrayList<>();

        // Compare sections
        for (FilingSection currentSection : currentSections) {
            Optional<FilingSection> previousSectionOpt = previousSections.stream()
                    .filter(s -> s.getSection().equals(currentSection.getSection()))
                    .findFirst();

            if (previousSectionOpt.isEmpty()) {
                // Entire section is new
                deltas.add(createSectionAddedDelta(filingId, currentSection));
            } else {
                // Compare section text
                deltas.addAll(compareSectionText(filingId, currentSection, previousSectionOpt.get()));
            }
        }

        // Check for deleted sections
        for (FilingSection previousSection : previousSections) {
            boolean existsInCurrent = currentSections.stream()
                    .anyMatch(s -> s.getSection().equals(previousSection.getSection()));

            if (!existsInCurrent) {
                deltas.add(createSectionDeletedDelta(filingId, previousSection));
            }
        }

        // Save deltas
        deltaRepository.saveAll(deltas);
        logger.info("Created {} deltas for filing {}", deltas.size(), filingId);

        return deltas;
    }

    /**
     * Get sections for a filing, extracting them if they don't exist
     */
    private List<FilingSection> getOrExtractSections(Long filingId) {
        List<FilingSection> sections = sectionRepository.findByFilingId(filingId);

        if (sections.isEmpty()) {
            logger.info("No sections found for filing {}, extracting...", filingId);
            try {
                sections = sectionExtractor.extractSections(filingId).block();
                if (sections == null) {
                    sections = new ArrayList<>();
                }
            } catch (Exception e) {
                logger.error("Failed to extract sections for filing {}: {}", filingId, e.getMessage());
                sections = new ArrayList<>();
            }
        }

        return sections;
    }

    private Optional<Filing> findPreviousFiling(Filing current) {
        String form = current.getForm();
        String cik = current.getCik();
        LocalDate periodEnd = current.getPeriodEnd();

        if (periodEnd == null) {
            // Use filed date as fallback if periodEnd is not available
            logger.warn("Filing {} has no periodEnd, using filedAt for comparison",
                current.getAccessionNo());
            return filingRepository.findByCikAndFormOrderByFiledAtDesc(cik, form).stream()
                    .filter(f -> f.getFiledAt().isBefore(current.getFiledAt()))
                    .findFirst();
        }

        // Use periodEnd-based query for more accurate comparison
        // This finds the most recent filing with periodEnd before the current filing's periodEnd
        // For 10-Q: finds previous quarter (e.g., 2024-06-30 when current is 2024-09-30)
        // For 10-K: finds previous year (e.g., 2023-12-31 when current is 2024-12-31)
        Optional<Filing> previous = filingRepository.findPreviousByPeriodEnd(cik, form, periodEnd);

        if (previous.isPresent()) {
            logger.debug("Found previous filing for comparison: {} (period: {}) vs {} (period: {})",
                current.getAccessionNo(), current.getPeriodEnd(),
                previous.get().getAccessionNo(), previous.get().getPeriodEnd());
        } else {
            logger.warn("No previous filing found for CIK {} form {} before period {}",
                cik, form, periodEnd);
        }

        return previous;
    }

    private List<FilingDelta> compareSectionText(Long filingId, FilingSection current, FilingSection previous) {
        List<FilingDelta> deltas = new ArrayList<>();

        if (current.getTextHash() != null && current.getTextHash().equals(previous.getTextHash())) {
            // Sections are identical
            logger.debug("Section {} unchanged", current.getSection());
            return deltas;
        }

        // Use diff-match-patch library
        DiffMatchPatch dmp = new DiffMatchPatch();
        LinkedList<Diff> diffs = dmp.diffMain(previous.getText(), current.getText());
        dmp.diffCleanupSemantic(diffs); // Improve readability

        // Process diffs
        for (Diff diff : diffs) {
            if (diff.operation == Operation.EQUAL) {
                continue; // Skip unchanged text
            }

            String snippet = truncateSnippet(diff.text);
            if (snippet.length() < MIN_SNIPPET_LENGTH) {
                continue; // Skip trivial changes
            }

            FilingDelta delta = new FilingDelta();
            delta.setFilingId(filingId);
            delta.setSection(current.getSection());
            delta.setSnippet(snippet);

            if (diff.operation == Operation.INSERT) {
                delta.setOperation(FilingDelta.Operation.INSERT);
            } else if (diff.operation == Operation.DELETE) {
                delta.setOperation(FilingDelta.Operation.DELETE);
            }

            // Calculate importance score
            double score = sectionExtractor.calculateImportanceScore(snippet);
            delta.setScore(BigDecimal.valueOf(score).setScale(3, RoundingMode.HALF_UP));

            deltas.add(delta);
        }

        return deltas;
    }

    private FilingDelta createSectionAddedDelta(Long filingId, FilingSection section) {
        FilingDelta delta = new FilingDelta();
        delta.setFilingId(filingId);
        delta.setSection(section.getSection());
        delta.setOperation(FilingDelta.Operation.INSERT);
        delta.setSnippet(truncateSnippet("Entire section added: " + section.getSection()));
        delta.setScore(BigDecimal.valueOf(0.8)); // High importance for new sections
        return delta;
    }

    private FilingDelta createSectionDeletedDelta(Long filingId, FilingSection section) {
        FilingDelta delta = new FilingDelta();
        delta.setFilingId(filingId);
        delta.setSection(section.getSection());
        delta.setOperation(FilingDelta.Operation.DELETE);
        delta.setSnippet(truncateSnippet("Entire section removed: " + section.getSection()));
        delta.setScore(BigDecimal.valueOf(0.7)); // High importance for deleted sections
        return delta;
    }

    private String truncateSnippet(String text) {
        if (text.length() <= MAX_SNIPPET_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_SNIPPET_LENGTH) + "...";
    }

    /**
     * Get delta summary for a filing
     */
    public DeltaSummary getDeltaSummary(Long filingId) {
        List<FilingDelta> deltas = deltaRepository.findByFilingIdOrderByScoreDesc(filingId);

        DeltaSummary summary = new DeltaSummary();
        summary.setFilingId(filingId);
        summary.setTotalChanges(deltas.size());

        long inserts = deltas.stream().filter(d -> FilingDelta.Operation.INSERT.equals(d.getOperation())).count();
        long deletes = deltas.stream().filter(d -> FilingDelta.Operation.DELETE.equals(d.getOperation())).count();
        long modifies = deltas.stream().filter(d -> FilingDelta.Operation.MODIFY.equals(d.getOperation())).count();

        summary.setInsertCount((int) inserts);
        summary.setDeleteCount((int) deletes);
        summary.setModifyCount((int) modifies);

        // Get top changes
        summary.setTopChanges(deltas.stream().limit(10).toList());

        return summary;
    }

    public static class DeltaSummary {
        private Long filingId;
        private int totalChanges;
        private int insertCount;
        private int deleteCount;
        private int modifyCount;
        private List<FilingDelta> topChanges;

        // Getters and Setters
        public Long getFilingId() { return filingId; }
        public void setFilingId(Long filingId) { this.filingId = filingId; }

        public int getTotalChanges() { return totalChanges; }
        public void setTotalChanges(int totalChanges) { this.totalChanges = totalChanges; }

        public int getInsertCount() { return insertCount; }
        public void setInsertCount(int insertCount) { this.insertCount = insertCount; }

        public int getDeleteCount() { return deleteCount; }
        public void setDeleteCount(int deleteCount) { this.deleteCount = deleteCount; }

        public int getModifyCount() { return modifyCount; }
        public void setModifyCount(int modifyCount) { this.modifyCount = modifyCount; }

        public List<FilingDelta> getTopChanges() { return topChanges; }
        public void setTopChanges(List<FilingDelta> topChanges) { this.topChanges = topChanges; }
    }
}
