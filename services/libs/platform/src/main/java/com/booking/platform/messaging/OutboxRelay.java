package com.booking.platform.messaging;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.booking.platform.config.PlatformProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes committed outbox rows to RabbitMQ with publisher confirms, oldest first.
 * {@code FOR UPDATE SKIP LOCKED} lets several instances of a service relay in parallel
 * without sending a row twice; a failed publish rolls back and is retried on the next pass.
 * Delivery is at least once, so consumers de-duplicate with {@link Inbox}.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final JdbcClient jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate tx;
    private final PlatformProperties.Messaging settings;
    private final Clock clock;

    public OutboxRelay(JdbcClient jdbc, RabbitTemplate rabbit, TransactionTemplate tx,
            PlatformProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.tx = tx;
        this.settings = properties.messaging();
        this.clock = clock;
    }

    record Row(UUID id, String aggregateType, String aggregateId, String eventType, int eventVersion,
            String payload, String requestId, Timestamp occurredAt) {
    }

    @Scheduled(fixedDelayString = "${booking.platform.messaging.relay-interval:PT1S}",
            initialDelayString = "${booking.platform.messaging.relay-initial-delay:PT5S}")
    public void relayScheduled() {
        try {
            relay();
        } catch (RuntimeException e) {
            log.warn("Outbox relay pass failed; will retry: {}", e.toString());
        }
    }

    /** Sends one batch and returns how many events were published. */
    public int relay() {
        Integer sent = tx.execute(status -> {
            List<Row> rows = jdbc.sql("""
                    select id, aggregate_type, aggregate_id, event_type, event_version, payload::text as payload,
                           request_id, occurred_at
                    from outbox_event
                    where published_at is null
                    order by occurred_at, id
                    limit :limit
                    for update skip locked
                    """)
                    .param("limit", settings.relayBatchSize())
                    .query(Row.class)
                    .list();
            if (rows.isEmpty()) {
                return 0;
            }
            rabbit.invoke(ops -> {
                for (Row row : rows) {
                    ops.send(settings.exchange(), row.eventType(), toMessage(row));
                }
                ops.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
                return null;
            });
            jdbc.sql("update outbox_event set published_at = :now where id in (:ids)")
                    .param("now", Timestamp.from(clock.instant()))
                    .param("ids", rows.stream().map(Row::id).toList())
                    .update();
            return rows.size();
        });
        return sent == null ? 0 : sent;
    }

    @Scheduled(cron = "${booking.platform.messaging.purge-cron:0 17 3 * * *}")
    public void purge() {
        Timestamp cutoff = Timestamp.from(clock.instant().minus(settings.retention()));
        int outbox = jdbc.sql("delete from outbox_event where published_at < :cutoff").param("cutoff", cutoff).update();
        int inbox = jdbc.sql("delete from inbox_message where processed_at < :cutoff").param("cutoff", cutoff).update();
        log.info("Purged {} published outbox rows and {} inbox rows older than {}", outbox, inbox, settings.retention());
    }

    private static Message toMessage(Row row) {
        MessageBuilder builder = MessageBuilder.withBody(row.payload().getBytes(StandardCharsets.UTF_8));
        builder.setMessageId(row.id().toString())
                .setType(row.eventType())
                .setContentType("application/json")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setTimestamp(Date.from(row.occurredAt().toInstant()))
                .setHeader(EventHeaders.VERSION, row.eventVersion())
                .setHeader(EventHeaders.AGGREGATE_TYPE, row.aggregateType())
                .setHeader(EventHeaders.AGGREGATE_ID, row.aggregateId())
                .setHeader(EventHeaders.OCCURRED_AT, row.occurredAt().toInstant().toString());
        if (row.requestId() != null) {
            builder.setHeader(EventHeaders.REQUEST_ID, row.requestId());
        }
        return builder.build();
    }
}
