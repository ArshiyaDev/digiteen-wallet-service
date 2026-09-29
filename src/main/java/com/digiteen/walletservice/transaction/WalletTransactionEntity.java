package com.digiteen.walletservice.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_transactions")
public class WalletTransactionEntity {
    @Id
    private UUID id;
    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;
    @Column(name = "source_wallet_id")
    private UUID sourceWalletId;
    @Column(name = "target_wallet_id")
    private UUID targetWalletId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;
    @Column(nullable = false)
    private long amount;
    @Column(name = "source_balance_after")
    private Long sourceBalanceAfter;
    @Column(name = "target_balance_after")
    private Long targetBalanceAfter;
    @Column(name = "failure_code", length = 50)
    private String failureCode;
    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WalletTransactionEntity() {
    }

    private WalletTransactionEntity(UUID id, UUID requesterUserId, UUID sourceWalletId, UUID targetWalletId,
                                    TransactionType type, TransactionStatus status, long amount,
                                    Long sourceBalanceAfter, Long targetBalanceAfter, String failureCode,
                                    String traceId, Instant createdAt) {
        this.id = id;
        this.requesterUserId = requesterUserId;
        this.sourceWalletId = sourceWalletId;
        this.targetWalletId = targetWalletId;
        this.type = type;
        this.status = status;
        this.amount = amount;
        this.sourceBalanceAfter = sourceBalanceAfter;
        this.targetBalanceAfter = targetBalanceAfter;
        this.failureCode = failureCode;
        this.traceId = traceId;
        this.createdAt = createdAt;
    }

    public static WalletTransactionEntity succeeded(UUID requesterUserId, UUID sourceWalletId, UUID targetWalletId,
                                                     TransactionType type, long amount, Long sourceBalanceAfter,
                                                     Long targetBalanceAfter, String traceId, Instant now) {
        return new WalletTransactionEntity(UUID.randomUUID(), requesterUserId, sourceWalletId, targetWalletId,
                type, TransactionStatus.SUCCEEDED, amount, sourceBalanceAfter, targetBalanceAfter, null, traceId, now);
    }

    public static WalletTransactionEntity rejected(UUID requesterUserId, UUID sourceWalletId, UUID targetWalletId,
                                                    TransactionType type, long amount, long sourceBalanceAfter,
                                                    String failureCode, String traceId, Instant now) {
        return new WalletTransactionEntity(UUID.randomUUID(), requesterUserId, sourceWalletId, targetWalletId,
                type, TransactionStatus.REJECTED, amount, sourceBalanceAfter, null, failureCode, traceId, now);
    }

    public UUID getId() { return id; }
    public UUID getRequesterUserId() { return requesterUserId; }
    public UUID getSourceWalletId() { return sourceWalletId; }
    public UUID getTargetWalletId() { return targetWalletId; }
    public TransactionType getType() { return type; }
    public TransactionStatus getStatus() { return status; }
    public long getAmount() { return amount; }
    public Long getSourceBalanceAfter() { return sourceBalanceAfter; }
    public Long getTargetBalanceAfter() { return targetBalanceAfter; }
    public String getFailureCode() { return failureCode; }
    public String getTraceId() { return traceId; }
    public Instant getCreatedAt() { return createdAt; }
}
