package com.booking.auth.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Refresh tokens, stored as SHA-256 hashes only. */
@Repository
public class RefreshTokenRepository {

    private final JdbcClient jdbc;

    public RefreshTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record StoredToken(UUID id, UUID userId, UUID familyId, Instant expiresAt, Instant revokedAt) {
    }

    private static StoredToken map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Timestamp revoked = rs.getTimestamp("revoked_at");
        return new StoredToken(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getObject("family_id", UUID.class), rs.getTimestamp("expires_at").toInstant(),
                revoked == null ? null : revoked.toInstant());
    }

    /** Locks the row so two refreshes with the same token can't both succeed. */
    public Optional<StoredToken> lockByHash(String tokenHash) {
        return jdbc.sql("""
                select id, user_id, family_id, expires_at, revoked_at from refresh_token
                where token_hash = :hash for update
                """).param("hash", tokenHash).query(RefreshTokenRepository::map).optional();
    }

    public Optional<StoredToken> findByHash(String tokenHash) {
        return jdbc.sql("select id, user_id, family_id, expires_at, revoked_at from refresh_token where token_hash = :hash")
                .param("hash", tokenHash).query(RefreshTokenRepository::map).optional();
    }

    public void insert(UUID id, UUID userId, UUID familyId, String tokenHash, Instant createdAt, Instant expiresAt) {
        jdbc.sql("""
                insert into refresh_token (id, user_id, family_id, token_hash, created_at, expires_at)
                values (:id, :user, :family, :hash, :now, :expires)
                """)
                .param("id", id).param("user", userId).param("family", familyId).param("hash", tokenHash)
                .param("now", Timestamp.from(createdAt)).param("expires", Timestamp.from(expiresAt))
                .update();
    }

    public void markReplaced(UUID id, UUID replacedBy, Instant at) {
        jdbc.sql("update refresh_token set revoked_at = :now, replaced_by = :next where id = :id")
                .param("now", Timestamp.from(at)).param("next", replacedBy).param("id", id).update();
    }

    public int revokeFamily(UUID familyId, Instant at) {
        return jdbc.sql("update refresh_token set revoked_at = :now where family_id = :family and revoked_at is null")
                .param("now", Timestamp.from(at)).param("family", familyId).update();
    }
}
