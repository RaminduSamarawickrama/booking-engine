package com.booking.auth.tokens;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.booking.auth.config.AuthProperties;
import com.booking.platform.web.ApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque, single-use refresh tokens with rotation. Each refresh revokes the presented token
 * and issues its successor in the same family. Presenting an already-used token means it
 * was copied, so the whole family is revoked and the user must sign in again.
 */
@Component
public class RefreshTokens {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokens.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient jdbc;
    private final AuthProperties properties;
    private final Clock clock;

    public RefreshTokens(JdbcClient jdbc, AuthProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    public record Issued(String token, Instant expiresAt) {
    }

    record Stored(UUID id, UUID userId, UUID familyId, Timestamp expiresAt, Timestamp revokedAt) {
    }

    /** Starts a new family, e.g. at sign-in. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Issued issue(UUID userId) {
        return insert(userId, UUID.randomUUID()).issued();
    }

    /**
     * Consumes {@code token} and returns the user it belongs to plus its successor.
     * Must run in a transaction so revoke-and-replace is atomic. A rejected token does not roll
     * the transaction back, so revoking a reused token's family still commits.
     */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiException.class)
    public Rotation rotate(String token) {
        Stored stored = find(token);
        Instant now = clock.instant();
        if (stored.revokedAt() != null) {
            int revoked = revokeFamily(stored.familyId());
            log.warn("Refresh token reuse for user {}; revoked {} live tokens in its family", stored.userId(), revoked);
            throw invalid("refresh_token_reused", "This session was signed out for your security. Sign in again.");
        }
        if (!stored.expiresAt().toInstant().isAfter(now)) {
            throw invalid("refresh_token_expired", "Your session has expired. Sign in again.");
        }
        Inserted next = insert(stored.userId(), stored.familyId());
        jdbc.sql("update refresh_token set revoked_at = :now, replaced_by = :next where id = :id")
                .param("now", Timestamp.from(now)).param("next", next.id()).param("id", stored.id()).update();
        return new Rotation(stored.userId(), next.issued());
    }

    public record Rotation(UUID userId, Issued next) {
    }

    /** Signs out the session the token belongs to. Unknown tokens are ignored. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(String token) {
        jdbc.sql("select id, user_id, family_id, expires_at, revoked_at from refresh_token where token_hash = :hash")
                .param("hash", hash(token)).query(Stored.class).optional()
                .ifPresent(stored -> revokeFamily(stored.familyId()));
    }

    private Stored find(String token) {
        return jdbc.sql("""
                select id, user_id, family_id, expires_at, revoked_at from refresh_token
                where token_hash = :hash for update
                """)
                .param("hash", hash(token)).query(Stored.class).optional()
                .orElseThrow(() -> invalid("invalid_refresh_token", "Sign in again."));
    }

    private int revokeFamily(UUID familyId) {
        return jdbc.sql("update refresh_token set revoked_at = :now where family_id = :family and revoked_at is null")
                .param("now", Timestamp.from(clock.instant())).param("family", familyId).update();
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
        jdbc.sql("""
                insert into refresh_token (id, user_id, family_id, token_hash, created_at, expires_at)
                values (:id, :user, :family, :hash, :now, :expires)
                """)
                .param("id", id).param("user", userId).param("family", familyId).param("hash", hash(token))
                .param("now", Timestamp.from(now)).param("expires", Timestamp.from(expiresAt))
                .update();
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
