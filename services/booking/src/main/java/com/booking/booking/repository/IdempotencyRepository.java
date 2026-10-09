package com.booking.booking.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Stored responses for requests sent with an Idempotency-Key. */
@Repository
public class IdempotencyRepository {

    private final JdbcClient jdbc;

    public IdempotencyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record StoredResponse(String requestHash, String response) {
    }

    public Optional<StoredResponse> find(String key) {
        return jdbc.sql("select request_hash, response::text as response from idempotent_request where key = :key")
                .param("key", key).query(StoredResponse.class).optional();
    }

    /** @return false if another request stored a response under this key first */
    public boolean insert(String key, String requestHash, String responseJson, Instant at) {
        try {
            jdbc.sql("""
                    insert into idempotent_request (key, request_hash, response, created_at)
                    values (:key, :hash, cast(:response as jsonb), :at)
                    """)
                    .param("key", key).param("hash", requestHash).param("response", responseJson)
                    .param("at", Timestamp.from(at)).update();
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public int deleteOlderThan(Instant cutoff) {
        return jdbc.sql("delete from idempotent_request where created_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff)).update();
    }
}
