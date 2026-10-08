package com.booking.platform.messaging;

/**
 * An event a service publishes about one of its aggregates. Implementations are records
 * whose fields form the JSON payload; {@link #type()} is the routing key, e.g.
 * {@code booking.confirmed}, and {@link #version()} changes only on breaking payload changes.
 */
public interface DomainEvent {

    String type();

    default int version() {
        return 1;
    }

    String aggregateType();

    String aggregateId();
}
