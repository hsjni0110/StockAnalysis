package com.stockdelta.common.repository;

import com.stockdelta.common.entity.FilingSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FilingSectionRepository extends JpaRepository<FilingSection, Long> {

    List<FilingSection> findByFilingId(Long filingId);

    Optional<FilingSection> findByFilingIdAndSection(Long filingId, String section);

    @Query("SELECT fs FROM FilingSection fs WHERE fs.filingId = :filingId AND fs.section IN :sections")
    List<FilingSection> findByFilingIdAndSections(@Param("filingId") Long filingId,
                                                    @Param("sections") List<String> sections);

    boolean existsByFilingIdAndSection(Long filingId, String section);

    void deleteByFilingId(Long filingId);
}
