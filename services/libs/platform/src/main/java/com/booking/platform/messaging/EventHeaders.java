package com.booking.platform.messaging;

/** AMQP headers set on every published event, read by consumers. */
public final class EventHeaders {

    public static final String VERSION = "x-event-version";
    public static final String AGGREGATE_TYPE = "x-aggregate-type";
    public static final String AGGREGATE_ID = "x-aggregate-id";
    public static final String OCCURRED_AT = "x-occurred-at";
    public static final String REQUEST_ID = "x-request-id";

    private EventHeaders() {
    }
}
