package com.digiteen.walletservice.wallet;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<WalletEntity, UUID> {
    Optional<WalletEntity> findByUserId(UUID userId);

    @Query("select w.id from WalletEntity w where w.userId = :userId")
    Optional<UUID> findIdByUserId(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletEntity w where w.id = :id")
    Optional<WalletEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO wallets (id, user_id, balance, version, created_at, updated_at)
            VALUES (:id, :userId, 0, 0, :now, :now)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId, @Param("now") Instant now);
}
