package com.booking.pricing.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.booking.pricing.domain.Quote;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.json.JsonMapper;

/** Quotes are stored whole, so a booking charges exactly what the customer was shown. */
@Repository
public class QuoteRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public QuoteRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insert(Quote quote) {
        jdbc.sql("insert into quote (id, created_at, expires_at, body) values (:id, :created, :expires, cast(:body as jsonb))")
                .param("id", quote.id())
                .param("created", Timestamp.from(quote.createdAt()))
                .param("expires", Timestamp.from(quote.expiresAt()))
                .param("body", json.writeValueAsString(quote))
                .update();
    }

    public Optional<Quote> findById(UUID id) {
        return jdbc.sql("select body::text from quote where id = :id").param("id", id)
                .query(String.class).optional().map(body -> json.readValue(body, Quote.class));
    }

    public int deleteExpiredBefore(Instant cutoff) {
        return jdbc.sql("delete from quote where expires_at < :cutoff").param("cutoff", Timestamp.from(cutoff)).update();
    }
}
