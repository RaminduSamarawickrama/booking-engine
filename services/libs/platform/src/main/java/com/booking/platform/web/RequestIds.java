package com.booking.platform.web;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;

/**
 * The request id ties together every log line, error response and event caused by one
 * incoming request. It arrives in the {@value #HEADER} header (set by the gateway), is kept
 * in the logging MDC under {@value #MDC_KEY}, and travels with outbox events.
 */
public final class RequestIds {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    private RequestIds() {
    }

    /** Accepts a caller-supplied id only if it is safe to log and echo; otherwise makes a new one. */
    public static String acceptOrCreate(String candidate) {
        if (candidate != null && SAFE.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }
}
