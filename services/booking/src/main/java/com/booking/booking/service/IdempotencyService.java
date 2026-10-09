package com.booking.booking.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.booking.booking.repository.IdempotencyRepository;
import com.booking.platform.error.ApiException;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

/**
 * Implements the {@code Idempotency-Key} header for booking creation. The first request with
 * a key stores its response; a retry with the same key and body gets that response back; the
 * same key with a different body is rejected. The stored response includes the guest's manage
 * token, so rows expire after a day.
 */
@Service
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{16,80}");
    private static final Duration KEEP = Duration.ofHours(24);

    private final IdempotencyRepository repository;
    private final JsonMapper json;
    private final Clock clock;

    public IdempotencyService(IdempotencyRepository repository, JsonMapper json, Clock clock) {
        this.repository = repository;
        this.json = json;
        this.clock = clock;
    }

    public record Result<T>(T body, boolean replayed) {
    }

    public <T> Result<T> run(String key, Object request, Class<T> type, Supplier<T> action) {
        if (key == null) {
            return new Result<>(action.get(), false);
        }
        if (!KEY.matcher(key).matches()) {
            throw ApiException.unprocessable("invalid_idempotency_key", "Idempotency-Key must be 16–80 letters, digits, - or _.");
        }
        String requestHash = sha256(json.writeValueAsString(request));
        Optional<T> previous = previous(key, requestHash, type);
        if (previous.isPresent()) {
            return new Result<>(previous.get(), true);
        }
        T body = action.get();
        if (repository.insert(key, requestHash, json.writeValueAsString(body), clock.instant())) {
            return new Result<>(body, false);
        }
        // A concurrent twin finished first. Ours also went through; report the first one.
        return previous(key, requestHash, type).map(b -> new Result<>(b, true))
                .orElseThrow(() -> new IllegalStateException("Idempotency key vanished: " + key));
    }

    private <T> Optional<T> previous(String key, String requestHash, Class<T> type) {
        return repository.find(key).map(stored -> {
            if (!stored.requestHash().equals(requestHash)) {
                throw ApiException.unprocessable("idempotency_key_reused",
                        "This Idempotency-Key was already used for a different request.");
            }
            return json.readValue(stored.response(), type);
        });
    }

    @Scheduled(cron = "0 41 * * * *")
    public void purge() {
        repository.deleteOlderThan(clock.instant().minus(KEEP));
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
