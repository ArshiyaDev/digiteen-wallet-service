package com.digiteen.walletservice.dto;

import java.util.UUID;

public record WalletResponse(UUID walletId, UUID userId, long balance, long version) {
}
