package com.digiteen.walletservice.idempotency;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, UUID> {
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO idempotency_records
                (id, user_id, idempotency_key, request_hash, created_at)
            VALUES (:id, :userId, :key, :requestHash, :now)
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId,
                       @Param("key") String key, @Param("requestHash") String requestHash,
                       @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from IdempotencyRecordEntity r
            where r.userId = :userId and r.idempotencyKey = :key
            """)
    Optional<IdempotencyRecordEntity> findForUpdate(@Param("userId") UUID userId, @Param("key") String key);
}
