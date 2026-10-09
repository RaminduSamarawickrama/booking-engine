package com.booking.gateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("booking.gateway")
public record GatewayProperties(String corsAllowedOriginPatterns, RateLimit rateLimit) {

    public GatewayProperties {
        corsAllowedOriginPatterns = corsAllowedOriginPatterns == null ? "" : corsAllowedOriginPatterns;
        rateLimit = rateLimit == null ? new RateLimit(true, 300, 10) : rateLimit;
    }

    public List<String> originPatterns() {
        return java.util.Arrays.stream(corsAllowedOriginPatterns.split(",")).map(String::strip)
                .filter(s -> !s.isEmpty()).toList();
    }

    public record RateLimit(boolean enabled, int defaultPerMinute, int signInPerMinute) {
    }
}
