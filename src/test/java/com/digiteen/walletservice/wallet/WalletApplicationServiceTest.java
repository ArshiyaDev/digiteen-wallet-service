package com.digiteen.walletservice.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.digiteen.walletservice.dto.TransactionResponse;
import com.digiteen.walletservice.idempotency.IdempotencyRecordEntity;
import com.digiteen.walletservice.idempotency.IdempotencyRecordRepository;
import com.digiteen.walletservice.outbox.OutboxEventEntity;
import com.digiteen.walletservice.outbox.OutboxEventRepository;
import com.digiteen.walletservice.transaction.TransactionStatus;
import com.digiteen.walletservice.transaction.TransactionType;
import com.digiteen.walletservice.transaction.WalletTransactionEntity;
import com.digiteen.walletservice.transaction.WalletTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WalletApplicationServiceTest {
    private WalletRepository wallets;
    private WalletTransactionRepository transactions;
    private IdempotencyRecordRepository idempotency;
    private OutboxEventRepository outbox;
    private WalletApplicationService service;

    @BeforeEach
    void setUp() {
        wallets = mock(WalletRepository.class);
        transactions = mock(WalletTransactionRepository.class);
        idempotency = mock(IdempotencyRecordRepository.class);
        outbox = mock(OutboxEventRepository.class);
        service = new WalletApplicationService(
                wallets, transactions, idempotency, outbox, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void insufficientWithdrawalIsRecordedWithoutChangingBalanceOrCreatingAnEvent() {
        UUID userId = UUID.randomUUID();
        WalletEntity wallet = WalletEntity.create(userId, Instant.now());
        wallet.credit(1_000, Instant.now());
        prepareIdempotency(userId, "withdraw-1");
        when(wallets.findIdByUserId(userId)).thenReturn(Optional.of(wallet.getId()));
        when(wallets.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = service.withdraw(userId, 3_000, "withdraw-1");

        assertThat(response.status()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(response.failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(wallet.getBalance()).isEqualTo(1_000);
        verify(outbox, never()).save(any());
    }

    @Test
    void successfulTransferLocksWalletsInStableOrderAndCreatesOneOutboxEvent() {
        UUID userId = UUID.randomUUID();
        WalletEntity source = WalletEntity.create(userId, Instant.now());
        WalletEntity target = WalletEntity.create(UUID.randomUUID(), Instant.now());
        source.credit(5_000, Instant.now());
        prepareIdempotency(userId, "transfer-1");
        when(wallets.findIdByUserId(userId)).thenReturn(Optional.of(source.getId()));
        when(wallets.existsById(target.getId())).thenReturn(true);
        when(wallets.findByIdForUpdate(any())).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.of(id.equals(source.getId()) ? source : target);
        });
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = service.transfer(userId, target.getId(), 1_000, "transfer-1");

        assertThat(response.status()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(source.getBalance()).isEqualTo(4_000);
        assertThat(target.getBalance()).isEqualTo(1_000);
        ArgumentCaptor<UUID> lockOrder = ArgumentCaptor.forClass(UUID.class);
        verify(wallets, times(2)).findByIdForUpdate(lockOrder.capture());
        List<UUID> expected = source.getId().compareTo(target.getId()) < 0
                ? List.of(source.getId(), target.getId()) : List.of(target.getId(), source.getId());
        assertThat(lockOrder.getAllValues()).containsExactlyElementsOf(expected);
        verify(outbox).save(any(OutboxEventEntity.class));
    }

    @Test
    void completedIdempotentRequestReplaysOriginalTransactionWithoutTouchingWallet() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        WalletTransactionEntity existing = WalletTransactionEntity.succeeded(
                userId, walletId, null, TransactionType.DEPOSIT, 500, 500L,
                null, "trace", Instant.now());
        IdempotencyRecordEntity record = prepareIdempotency(userId, "deposit-1");
        when(record.getTransactionId()).thenReturn(existing.getId());
        when(transactions.findById(existing.getId())).thenReturn(Optional.of(existing));

        TransactionResponse response = service.deposit(userId, 500, "deposit-1");

        assertThat(response.transactionId()).isEqualTo(existing.getId());
        verify(wallets, never()).insertIfAbsent(any(), any(), any());
        verify(outbox, never()).save(any());
    }

    private IdempotencyRecordEntity prepareIdempotency(UUID userId, String key) {
        AtomicReference<String> requestHash = new AtomicReference<>();
        IdempotencyRecordEntity record = mock(IdempotencyRecordEntity.class);
        when(idempotency.insertIfAbsent(any(), eq(userId), eq(key), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    return 1;
                });
        when(record.getRequestHash()).thenAnswer(invocation -> requestHash.get());
        when(idempotency.findForUpdate(userId, key)).thenReturn(Optional.of(record));
        return record;
    }
}
