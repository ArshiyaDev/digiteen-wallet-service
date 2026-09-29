package com.digiteen.walletservice.transaction;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransactionRepository extends JpaRepository<WalletTransactionEntity, UUID> {
    @Query("""
            select t from WalletTransactionEntity t
            where t.sourceWalletId = :walletId or t.targetWalletId = :walletId
            order by t.createdAt desc
            """)
    Page<WalletTransactionEntity> findHistory(@Param("walletId") UUID walletId, Pageable pageable);
}
