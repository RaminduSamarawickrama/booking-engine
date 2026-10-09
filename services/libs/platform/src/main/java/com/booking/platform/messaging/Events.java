package com.booking.platform.messaging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.booking.platform.web.RequestIds;

import org.slf4j.MDC;
import org.springframework.amqp.core.Message;

import tools.jackson.databind.json.JsonMapper;

/** Helpers for consumers: read an event payload and carry its request id into the logs. */
public final class Events {

    private Events() {
    }

    public static <T> T payload(Message message, Class<T> type, JsonMapper json) {
        return json.readValue(new String(message.getBody(), StandardCharsets.UTF_8), type);
    }

    public static UUID id(Message message) {
        return UUID.fromString(message.getMessageProperties().getMessageId());
    }

    public static String type(Message message) {
        return message.getMessageProperties().getType();
    }

    /** Runs {@code handler} with the event's request id in the MDC, as an HTTP request would have. */
    public static void handle(Message message, Runnable handler) {
        Object requestId = message.getMessageProperties().getHeaders().get(EventHeaders.REQUEST_ID);
        if (requestId != null) {
            MDC.put(RequestIds.MDC_KEY, requestId.toString());
        }
        try {
            handler.run();
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }
}
