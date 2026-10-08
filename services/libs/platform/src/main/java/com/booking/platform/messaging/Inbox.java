package com.booking.platform.messaging;

import java.util.UUID;

import org.springframework.amqp.core.Message;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes event handling idempotent. Call {@link #firstDelivery} at the start of a
 * {@code @Transactional} handler and return early when it is false: the inbox row and the
 * handler's own changes commit together, so a redelivered event is skipped exactly when its
 * effects are already stored.
 */
public class Inbox {

    private final JdbcClient jdbc;

    public Inbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(UUID messageId, String consumer) {
        return jdbc.sql("""
                insert into inbox_message (message_id, consumer) values (:id, :consumer)
                on conflict do nothing
                """)
                .param("id", messageId)
                .param("consumer", consumer)
                .update() == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(Message message, String consumer) {
        String id = message.getMessageProperties().getMessageId();
        if (id == null) {
            throw new IllegalArgumentException("Event has no message id; it did not come from an outbox");
        }
        return firstDelivery(UUID.fromString(id), consumer);
    }
}
