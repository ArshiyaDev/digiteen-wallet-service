package com.digiteen.walletservice.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consumed_events")
public class ConsumedEventEntity {
    @Id
    @Column(name = "event_id")
    private UUID eventId;
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;
    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    protected ConsumedEventEntity() {
    }
}
