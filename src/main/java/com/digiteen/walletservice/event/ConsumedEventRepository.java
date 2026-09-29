package com.digiteen.walletservice.event;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsumedEventRepository extends JpaRepository<ConsumedEventEntity, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO consumed_events (event_id, event_type, trace_id, consumed_at)
            VALUES (:eventId, :eventType, :traceId, :now)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("eventType") String eventType,
                       @Param("traceId") String traceId, @Param("now") Instant now);
}
