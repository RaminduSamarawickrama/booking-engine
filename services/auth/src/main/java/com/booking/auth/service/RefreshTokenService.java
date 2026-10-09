package com.booking.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.booking.auth.config.AuthProperties;
import com.booking.auth.repository.RefreshTokenRepository;
import com.booking.platform.error.ApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque, single-use refresh tokens with rotation. Each refresh revokes the presented token
 * and issues its successor in the same family. Presenting an already-used token means it
 * was copied, so the whole family is revoked and the user must sign in again.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final AuthProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, AuthProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    public record Issued(String token, Instant expiresAt) {
    }

    public record Rotation(UUID userId, Issued next) {
    }

    /** Starts a new family, e.g. at sign-in. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Issued issue(UUID userId) {
        return insert(userId, UUID.randomUUID()).issued();
    }

    /**
     * Consumes {@code token} and returns the user it belongs to plus its successor. A rejected
     * token does not roll the transaction back, so revoking a reused token's family still commits.
     */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiException.class)
    public Rotation rotate(String token) {
        RefreshTokenRepository.StoredToken stored = tokens.lockByHash(hash(token))
                .orElseThrow(() -> invalid("invalid_refresh_token", "Sign in again."));
        Instant now = clock.instant();
        if (stored.revokedAt() != null) {
            int revoked = tokens.revokeFamily(stored.familyId(), now);
            log.warn("Refresh token reuse for user {}; revoked {} live tokens in its family", stored.userId(), revoked);
            throw invalid("refresh_token_reused", "This session was signed out for your security. Sign in again.");
        }
        if (!stored.expiresAt().isAfter(now)) {
            throw invalid("refresh_token_expired", "Your session has expired. Sign in again.");
        }
        Inserted next = insert(stored.userId(), stored.familyId());
        tokens.markReplaced(stored.id(), next.id(), now);
        return new Rotation(stored.userId(), next.issued());
    }

    /** Signs out the session the token belongs to. Unknown tokens are ignored. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(String token) {
        tokens.findByHash(hash(token)).ifPresent(stored -> tokens.revokeFamily(stored.familyId(), clock.instant()));
    }

    private record Inserted(UUID id, Issued issued) {
    }

    private Inserted insert(UUID userId, UUID familyId) {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        tokens.insert(id, userId, familyId, hash(token), now, expiresAt);
        return new Inserted(id, new Issued(token, expiresAt));
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }
}
