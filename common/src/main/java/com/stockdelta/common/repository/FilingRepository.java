package com.stockdelta.common.repository;

import com.stockdelta.common.entity.Filing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FilingRepository extends JpaRepository<Filing, Long> {

    Optional<Filing> findByAccessionNo(String accessionNo);

    List<Filing> findByCikOrderByFiledAtDesc(String cik);

    List<Filing> findByCikAndFormOrderByFiledAtDesc(String cik, String form);

    @Query("SELECT f FROM Filing f WHERE f.cik = :cik AND f.form = :form AND f.filedAt >= :since ORDER BY f.filedAt DESC")
    List<Filing> findByCikAndFormSince(@Param("cik") String cik,
                                       @Param("form") String form,
                                       @Param("since") LocalDateTime since);

    @Query("SELECT f FROM Filing f WHERE f.cik IN :ciks AND f.filedAt >= :since ORDER BY f.filedAt DESC")
    List<Filing> findByCiksAndFiledAtAfter(@Param("ciks") List<String> ciks,
                                           @Param("since") LocalDateTime since);

    @Query("SELECT f FROM Filing f WHERE f.form IN :forms AND f.filedAt >= :since ORDER BY f.filedAt DESC")
    List<Filing> findByFormsAndFiledAtAfter(@Param("forms") List<String> forms,
                                            @Param("since") LocalDateTime since);

    boolean existsByAccessionNo(String accessionNo);

    @Query("SELECT COUNT(f) FROM Filing f WHERE f.cik = :cik")
    long countByCik(@Param("cik") String cik);

    /**
     * Find the most recent filing before the given period end date
     * Used for finding previous quarter/year filing for comparison
     */
    @Query("SELECT f FROM Filing f WHERE f.cik = :cik AND f.form = :form " +
           "AND f.periodEnd < :currentPeriodEnd " +
           "ORDER BY f.periodEnd DESC LIMIT 1")
    Optional<Filing> findPreviousByPeriodEnd(@Param("cik") String cik,
                                             @Param("form") String form,
                                             @Param("currentPeriodEnd") LocalDate currentPeriodEnd);

    /**
     * Find top N filings for a given CIK and form type
     * Ordered by filing date descending (most recent first)
     */
    @Query("SELECT f FROM Filing f WHERE f.cik = :cik AND f.form = :form " +
           "ORDER BY f.filedAt DESC LIMIT :limit")
    List<Filing> findTopNByCikAndForm(@Param("cik") String cik,
                                      @Param("form") String form,
                                      @Param("limit") int limit);
}