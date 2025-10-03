package com.stockdelta.common.repository;

import com.stockdelta.common.entity.Issuer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface IssuerRepository extends JpaRepository<Issuer, String> {

    Optional<Issuer> findByTicker(String ticker);

    Optional<Issuer> findByCik(String cik);

    List<Issuer> findByTickerIn(List<String> tickers);

    @Query("SELECT i FROM Issuer i WHERE i.ticker IS NOT NULL ORDER BY i.ticker")
    List<Issuer> findAllWithTicker();

    @Query("SELECT i FROM Issuer i WHERE i.cik = :cik OR i.ticker = :symbol")
    Optional<Issuer> findByCikOrTicker(@Param("cik") String cik, @Param("symbol") String symbol);

    boolean existsByTicker(String ticker);
}