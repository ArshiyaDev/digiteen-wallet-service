package com.digiteen.walletservice.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {
    @Id
    private UUID id;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected OutboxEventEntity() {
    }

    public OutboxEventEntity(UUID id, UUID aggregateId, String eventType, String payload,
                             String traceId, Instant occurredAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.traceId = traceId;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getTraceId() { return traceId; }
    public Instant getOccurredAt() { return occurredAt; }

    public void markPublished(Instant now) {
        publishedAt = now;
        attempts++;
        lastError = null;
    }

    public void markFailed(String message) {
        attempts++;
        lastError = message == null ? "unknown" : message.substring(0, Math.min(message.length(), 1000));
    }
}
