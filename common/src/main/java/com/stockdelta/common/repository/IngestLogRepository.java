package com.stockdelta.common.repository;

import com.stockdelta.common.entity.IngestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface IngestLogRepository extends JpaRepository<IngestLog, UUID> {

    List<IngestLog> findByStatusOrderByRequestTimestampDesc(String status);

    @Query("SELECT il FROM IngestLog il WHERE il.requestTimestamp >= :since ORDER BY il.requestTimestamp DESC")
    List<IngestLog> findRecentLogs(LocalDateTime since);

    @Query("SELECT il FROM IngestLog il ORDER BY il.requestTimestamp DESC LIMIT 10")
    List<IngestLog> findTop10ByOrderByRequestTimestampDesc();
}