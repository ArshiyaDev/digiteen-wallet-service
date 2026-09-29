package com.digiteen.walletservice.dto;

import jakarta.validation.constraints.Positive;

public record AmountRequest(@Positive(message = "amount must be greater than zero") long amount) {
}
