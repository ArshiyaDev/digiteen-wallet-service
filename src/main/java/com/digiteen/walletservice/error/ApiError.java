package com.digiteen.walletservice.error;

public record ApiError(String code, String message, String traceId) {
}
