package com.digiteen.walletservice.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class WalletEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private long balance;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WalletEntity() {
    }

    static WalletEntity create(UUID userId, Instant now) {
        WalletEntity wallet = new WalletEntity();
        wallet.id = UUID.randomUUID();
        wallet.userId = userId;
        wallet.createdAt = now;
        wallet.updatedAt = now;
        return wallet;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public long getBalance() { return balance; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void credit(long amount, Instant now) {
        balance = Math.addExact(balance, amount);
        updatedAt = now;
    }

    public boolean debit(long amount, Instant now) {
        if (balance < amount) {
            return false;
        }
        balance -= amount;
        updatedAt = now;
        return true;
    }
}
