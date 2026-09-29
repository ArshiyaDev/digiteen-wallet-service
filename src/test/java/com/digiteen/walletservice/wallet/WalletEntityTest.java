package com.digiteen.walletservice.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletEntityTest {
    @Test
    void debitNeverMakesBalanceNegative() {
        WalletEntity wallet = WalletEntity.create(UUID.randomUUID(), Instant.now());
        wallet.credit(1_000, Instant.now());

        boolean applied = wallet.debit(1_001, Instant.now());

        assertThat(applied).isFalse();
        assertThat(wallet.getBalance()).isEqualTo(1_000);
    }

    @Test
    void successfulDebitChangesBalanceExactlyOnce() {
        WalletEntity wallet = WalletEntity.create(UUID.randomUUID(), Instant.now());
        wallet.credit(10_000, Instant.now());

        assertThat(wallet.debit(3_000, Instant.now())).isTrue();
        assertThat(wallet.getBalance()).isEqualTo(7_000);
    }

    @Test
    void creditRejectsLongOverflow() {
        WalletEntity wallet = WalletEntity.create(UUID.randomUUID(), Instant.now());
        wallet.credit(Long.MAX_VALUE, Instant.now());

        assertThatThrownBy(() -> wallet.credit(1, Instant.now()))
                .isInstanceOf(ArithmeticException.class);
    }
}
