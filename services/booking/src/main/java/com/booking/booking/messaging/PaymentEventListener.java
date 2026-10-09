package com.booking.booking.messaging;

import java.util.UUID;

import com.booking.booking.service.BookingService;
import com.booking.platform.messaging.Events;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Presentation layer for events: reads payment events off RabbitMQ and hands them to the
 * service layer, which de-duplicates and applies them in one transaction.
 */
@Component
public class PaymentEventListener {

    static final String QUEUE = "booking.payments";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaymentSucceeded(String paymentId, String bookingId, int amountMinor, String currency) {
    }

    private final BookingService bookings;
    private final JsonMapper json;

    public PaymentEventListener(BookingService bookings, JsonMapper json) {
        this.bookings = bookings;
        this.json = json;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = QUEUE, durable = "true",
                    arguments = @Argument(name = "x-dead-letter-exchange", value = "booking.dlx")),
            exchange = @Exchange(name = "booking.events", type = "topic"),
            key = "payment.succeeded"))
    public void onPaymentSucceeded(Message message) {
        Events.handle(message, () -> {
            PaymentSucceeded payment = Events.payload(message, PaymentSucceeded.class, json);
            bookings.paymentSucceeded(Events.id(message), QUEUE, UUID.fromString(payment.bookingId()),
                    payment.paymentId(), payment.amountMinor(), payment.currency());
        });
    }

    // Guest bookings are NOT linked to new accounts by email: auth-service does not verify
    // email addresses yet, so anyone could register a victim's address and see their trips.
    // A customer links a booking by opening it with its manage token while signed in (claim).
}
