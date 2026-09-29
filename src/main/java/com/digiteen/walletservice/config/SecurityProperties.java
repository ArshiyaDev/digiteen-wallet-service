package com.digiteen.walletservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String issuer, String audience, String jwkSetUri) {
}
