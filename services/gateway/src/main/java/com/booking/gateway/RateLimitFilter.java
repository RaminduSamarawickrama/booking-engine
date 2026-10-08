package com.booking.gateway;

import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client-IP token buckets held in memory: enough for one gateway instance in development
 * and demo. Running several instances would move the buckets to Redis.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> SIGN_IN_PATHS = Set.of("/v1/auth/login", "/v1/auth/register", "/v1/auth/refresh");
    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final GatewayProperties.RateLimit settings;
    private final Clock clock;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(GatewayProperties.RateLimit settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !settings.enabled() || "OPTIONS".equals(request.getMethod()) || "/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean signIn = SIGN_IN_PATHS.contains(request.getRequestURI());
        int perMinute = signIn ? settings.signInPerMinute() : settings.defaultPerMinute();
        String key = (signIn ? "auth:" : "api:") + request.getRemoteAddr();
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            buckets.clear();
        }
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(perMinute, clock.millis()));
        long retryAfterSeconds = bucket.tryTake(clock.millis());
        if (retryAfterSeconds == 0) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"urn:booking:problem:rate_limited","title":"Too Many Requests","status":429,\
                "code":"rate_limited","detail":"Too many requests. Try again in %d seconds."}""".formatted(retryAfterSeconds));
    }

    /** Refills continuously at capacity per minute. */
    static final class TokenBucket {
        private final int capacity;
        private double tokens;
        private long updatedAt;

        TokenBucket(int capacity, long now) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.updatedAt = now;
        }

        /** @return 0 if allowed, otherwise seconds until a token is available */
        synchronized long tryTake(long now) {
            double perMs = capacity / 60_000.0;
            tokens = Math.min(capacity, tokens + (now - updatedAt) * perMs);
            updatedAt = now;
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return Math.max(1, (long) Math.ceil((1 - tokens) / perMs / 1000));
        }
    }
}
