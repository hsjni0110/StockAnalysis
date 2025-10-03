package com.stockdelta.common.repository;

import com.stockdelta.common.entity.XbrlFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface XbrlFactRepository extends JpaRepository<XbrlFact, Long> {

    List<XbrlFact> findByFilingId(Long filingId);

    List<XbrlFact> findByFilingIdAndTag(Long filingId, String tag);

    @Query("SELECT xf FROM XbrlFact xf WHERE xf.filingId = :filingId AND xf.tag IN :tags")
    List<XbrlFact> findByFilingIdAndTags(@Param("filingId") Long filingId,
                                          @Param("tags") List<String> tags);

    @Query("SELECT xf FROM XbrlFact xf WHERE xf.filingId = :filingId AND xf.taxonomy = :taxonomy")
    List<XbrlFact> findByFilingIdAndTaxonomy(@Param("filingId") Long filingId,
                                               @Param("taxonomy") String taxonomy);

    void deleteByFilingId(Long filingId);
}
