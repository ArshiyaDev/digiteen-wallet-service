package com.digiteen.walletservice.wallet;

import com.digiteen.walletservice.dto.TransactionResponse;
import com.digiteen.walletservice.dto.WalletResponse;
import com.digiteen.walletservice.error.ApiException;
import com.digiteen.walletservice.idempotency.IdempotencyRecordEntity;
import com.digiteen.walletservice.idempotency.IdempotencyRecordRepository;
import com.digiteen.walletservice.observability.TraceContext;
import com.digiteen.walletservice.outbox.OutboxEventEntity;
import com.digiteen.walletservice.outbox.OutboxEventRepository;
import com.digiteen.walletservice.transaction.TransactionStatus;
import com.digiteen.walletservice.transaction.TransactionType;
import com.digiteen.walletservice.transaction.WalletTransactionEntity;
import com.digiteen.walletservice.transaction.WalletTransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletApplicationService {
    private static final Logger log = LoggerFactory.getLogger(WalletApplicationService.class);
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final OutboxEventRepository outboxEvents;
    private final ObjectMapper objectMapper;

    public WalletApplicationService(WalletRepository wallets, WalletTransactionRepository transactions,
                                    IdempotencyRecordRepository idempotencyRecords,
                                    OutboxEventRepository outboxEvents, ObjectMapper objectMapper) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.idempotencyRecords = idempotencyRecords;
        this.outboxEvents = outboxEvents;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WalletResponse getOrCreateWallet(UUID userId) {
        UUID walletId = ensureWalletId(userId, Instant.now());
        WalletEntity wallet = wallets.findById(walletId).orElseThrow();
        return toWalletResponse(wallet);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> history(UUID userId, Pageable pageable) {
        WalletEntity wallet = wallets.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found"));
        return transactions.findHistory(wallet.getId(), pageable).map(this::toTransactionResponse);
    }

    @Transactional
    public TransactionResponse deposit(UUID userId, long amount, String key) {
        Instant now = Instant.now();
        IdempotencyRecordEntity idempotency = beginIdempotent(userId, key, hash("DEPOSIT", amount));
        TransactionResponse replay = replay(idempotency);
        if (replay != null) return replay;

        WalletEntity wallet = lockWallet(ensureWalletId(userId, now));
        wallet.credit(amount, now);
        WalletTransactionEntity transaction = transactions.save(WalletTransactionEntity.succeeded(
                userId, wallet.getId(), null, TransactionType.DEPOSIT, amount,
                wallet.getBalance(), null, TraceContext.currentTraceId(), now));
        completeSuccess(idempotency, transaction, wallet.getBalance(), null);
        logOperation(transaction);
        return toTransactionResponse(transaction);
    }

    @Transactional
    public TransactionResponse withdraw(UUID userId, long amount, String key) {
        Instant now = Instant.now();
        IdempotencyRecordEntity idempotency = beginIdempotent(userId, key, hash("WITHDRAWAL", amount));
        TransactionResponse replay = replay(idempotency);
        if (replay != null) return replay;

        WalletEntity wallet = lockWallet(ensureWalletId(userId, now));
        WalletTransactionEntity transaction;
        if (!wallet.debit(amount, now)) {
            transaction = transactions.save(WalletTransactionEntity.rejected(userId, wallet.getId(), null,
                    TransactionType.WITHDRAWAL, amount, wallet.getBalance(), "INSUFFICIENT_FUNDS",
                    TraceContext.currentTraceId(), now));
            idempotency.complete(transaction.getId());
        } else {
            transaction = transactions.save(WalletTransactionEntity.succeeded(userId, wallet.getId(), null,
                    TransactionType.WITHDRAWAL, amount, wallet.getBalance(), null,
                    TraceContext.currentTraceId(), now));
            completeSuccess(idempotency, transaction, wallet.getBalance(), null);
        }
        logOperation(transaction);
        return toTransactionResponse(transaction);
    }

    @Transactional
    public TransactionResponse transfer(UUID userId, UUID targetWalletId, long amount, String key) {
        Instant now = Instant.now();
        IdempotencyRecordEntity idempotency = beginIdempotent(
                userId, key, hash("TRANSFER", targetWalletId, amount));
        TransactionResponse replay = replay(idempotency);
        if (replay != null) return replay;

        UUID sourceWalletId = ensureWalletId(userId, now);
        if (sourceWalletId.equals(targetWalletId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_WALLET_TRANSFER",
                    "Source and target wallet must be different");
        }
        if (!wallets.existsById(targetWalletId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TARGET_WALLET_NOT_FOUND", "Target wallet not found");
        }

        UUID firstId = sourceWalletId.compareTo(targetWalletId) < 0 ? sourceWalletId : targetWalletId;
        UUID secondId = firstId.equals(sourceWalletId) ? targetWalletId : sourceWalletId;
        WalletEntity first = lockWallet(firstId);
        WalletEntity second = lockWallet(secondId);
        WalletEntity source = first.getId().equals(sourceWalletId) ? first : second;
        WalletEntity target = first.getId().equals(targetWalletId) ? first : second;

        WalletTransactionEntity transaction;
        if (!source.debit(amount, now)) {
            transaction = transactions.save(WalletTransactionEntity.rejected(userId, source.getId(), target.getId(),
                    TransactionType.TRANSFER, amount, source.getBalance(), "INSUFFICIENT_FUNDS",
                    TraceContext.currentTraceId(), now));
            idempotency.complete(transaction.getId());
        } else {
            target.credit(amount, now);
            transaction = transactions.save(WalletTransactionEntity.succeeded(userId, source.getId(), target.getId(),
                    TransactionType.TRANSFER, amount, source.getBalance(), target.getBalance(),
                    TraceContext.currentTraceId(), now));
            completeSuccess(idempotency, transaction, source.getBalance(), target.getBalance());
        }
        logOperation(transaction);
        return toTransactionResponse(transaction);
    }

    private UUID ensureWalletId(UUID userId, Instant now) {
        wallets.insertIfAbsent(UUID.randomUUID(), userId, now);
        return wallets.findIdByUserId(userId).orElseThrow();
    }

    private WalletEntity lockWallet(UUID walletId) {
        return wallets.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found"));
    }

    private IdempotencyRecordEntity beginIdempotent(UUID userId, String key, String requestHash) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 1 to 100 characters");
        }
        idempotencyRecords.insertIfAbsent(UUID.randomUUID(), userId, key, requestHash, Instant.now());
        IdempotencyRecordEntity record = idempotencyRecords.findForUpdate(userId, key).orElseThrow();
        if (!record.getRequestHash().equals(requestHash)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "The Idempotency-Key was already used for a different request");
        }
        return record;
    }

    private TransactionResponse replay(IdempotencyRecordEntity record) {
        if (record.getTransactionId() == null) return null;
        WalletTransactionEntity transaction = transactions.findById(record.getTransactionId()).orElseThrow();
        log.atInfo().addKeyValue("event", "idempotent_replay")
                .addKeyValue("transactionId", transaction.getId())
                .log("Idempotent response replayed");
        return toTransactionResponse(transaction);
    }

    private void completeSuccess(IdempotencyRecordEntity idempotency, WalletTransactionEntity transaction,
                                 Long sourceBalanceAfter, Long targetBalanceAfter) {
        idempotency.complete(transaction.getId());
        UUID eventId = UUID.randomUUID();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId);
        event.put("eventType", "WalletTransactionSucceeded");
        event.put("eventVersion", 1);
        event.put("transactionId", transaction.getId());
        event.put("transactionType", transaction.getType());
        event.put("sourceWalletId", transaction.getSourceWalletId());
        event.put("targetWalletId", transaction.getTargetWalletId());
        event.put("amount", transaction.getAmount());
        event.put("sourceBalanceAfter", sourceBalanceAfter);
        event.put("targetBalanceAfter", targetBalanceAfter);
        event.put("traceId", transaction.getTraceId());
        event.put("occurredAt", transaction.getCreatedAt());
        try {
            outboxEvents.save(new OutboxEventEntity(eventId, transaction.getId(),
                    "WalletTransactionSucceeded", objectMapper.writeValueAsString(event),
                    transaction.getTraceId(), transaction.getCreatedAt()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize wallet event", exception);
        }
    }

    private String hash(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void logOperation(WalletTransactionEntity transaction) {
        log.atInfo().addKeyValue("event", "wallet_transaction_completed")
                .addKeyValue("transactionId", transaction.getId()).addKeyValue("type", transaction.getType())
                .addKeyValue("status", transaction.getStatus()).addKeyValue("amount", transaction.getAmount())
                .log("Wallet transaction completed");
    }

    private WalletResponse toWalletResponse(WalletEntity wallet) {
        return new WalletResponse(wallet.getId(), wallet.getUserId(), wallet.getBalance(), wallet.getVersion());
    }

    private TransactionResponse toTransactionResponse(WalletTransactionEntity transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getType(), transaction.getStatus(),
                transaction.getAmount(), transaction.getSourceWalletId(), transaction.getTargetWalletId(),
                transaction.getSourceBalanceAfter(), transaction.getTargetBalanceAfter(),
                transaction.getFailureCode(), transaction.getTraceId(), transaction.getCreatedAt());
    }
}
