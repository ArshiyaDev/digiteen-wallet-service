package com.digiteen.walletservice.dto;

import com.digiteen.walletservice.transaction.TransactionStatus;
import com.digiteen.walletservice.transaction.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(UUID transactionId, TransactionType type, TransactionStatus status,
                                  long amount, UUID sourceWalletId, UUID targetWalletId,
                                  Long sourceBalanceAfter, Long targetBalanceAfter,
                                  String failureCode, String traceId, Instant createdAt) {
}
