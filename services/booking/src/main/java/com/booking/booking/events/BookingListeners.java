package com.booking.booking.events;

import java.util.UUID;

import com.booking.booking.Bookings;
import com.booking.platform.messaging.Events;
import com.booking.platform.messaging.Inbox;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

/** Events booking-service reacts to. Each handler runs in one transaction with its inbox entry. */
@Component
public class BookingListeners {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaymentSucceeded(String paymentId, String bookingId, int amountMinor, String currency) {
    }

    private final Bookings bookings;
    private final Inbox inbox;
    private final TransactionTemplate tx;
    private final JsonMapper json;

    public BookingListeners(Bookings bookings, Inbox inbox, TransactionTemplate tx, JsonMapper json) {
        this.bookings = bookings;
        this.inbox = inbox;
        this.tx = tx;
        this.json = json;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "booking.payments", durable = "true",
                    arguments = @Argument(name = "x-dead-letter-exchange", value = "booking.dlx")),
            exchange = @Exchange(name = "booking.events", type = "topic"),
            key = "payment.succeeded"))
    public void onPayment(Message message) {
        Events.handle(message, () -> tx.executeWithoutResult(status -> {
            if (!inbox.firstDelivery(message, "booking.payments")) {
                return;
            }
            PaymentSucceeded payment = Events.payload(message, PaymentSucceeded.class, json);
            bookings.paymentSucceeded(UUID.fromString(payment.bookingId()), payment.paymentId(), payment.amountMinor(),
                    payment.currency());
        }));
    }

    // Guest bookings are NOT linked to new accounts by email: auth-service does not verify
    // email addresses yet, so anyone could register a victim's address and see their trips.
    // A customer links a booking by opening it with its manage token while signed in (claim).
}
