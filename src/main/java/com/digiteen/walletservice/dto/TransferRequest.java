package com.digiteen.walletservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record TransferRequest(@NotNull UUID targetWalletId,
                              @Positive(message = "amount must be greater than zero") long amount) {
}
