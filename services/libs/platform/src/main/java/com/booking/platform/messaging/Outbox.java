package com.booking.platform.messaging;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.booking.platform.web.RequestIds;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Records events in the service's own database, in the same transaction as the state change
 * that caused them. {@link OutboxRelay} publishes them afterwards, so an event is sent if and
 * only if the change committed — no dual write.
 */
public class Outbox {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public Outbox(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /** Must run inside the caller's transaction; fails fast if there is none. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(DomainEvent event) {
        UUID id = UUID.randomUUID();
        Instant occurredAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        jdbc.sql("""
                insert into outbox_event (id, aggregate_type, aggregate_id, event_type, event_version,
                                          payload, request_id, occurred_at)
                values (:id, :aggregateType, :aggregateId, :type, :version, cast(:payload as jsonb), :requestId, :occurredAt)
                """)
                .param("id", id)
                .param("aggregateType", event.aggregateType())
                .param("aggregateId", event.aggregateId())
                .param("type", event.type())
                .param("version", event.version())
                .param("payload", json.writeValueAsString(event))
                .param("requestId", RequestIds.current().orElse(null))
                .param("occurredAt", java.sql.Timestamp.from(occurredAt))
                .update();
        return id;
    }
}
