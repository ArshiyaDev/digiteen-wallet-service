package com.digiteen.walletservice.observability;

import java.util.Optional;
import org.slf4j.MDC;

public final class TraceContext {
    private TraceContext() { }
    public static String currentTraceId() {
        return Optional.ofNullable(MDC.get(TraceIdFilter.MDC_KEY)).orElse("system");
    }
}
