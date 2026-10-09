package com.booking.payment.messaging;

import java.util.UUID;

import com.booking.payment.service.PaymentService;
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

/** booking.payment_rejected: the booking couldn't take a payment, so it is refunded. */
@Component
public class BookingEventListener {

    static final String QUEUE = "payment.booking-events";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaymentRejected(String bookingId, String paymentId, String reason) {
    }

    private final PaymentService payments;
    private final JsonMapper json;

    public BookingEventListener(PaymentService payments, JsonMapper json) {
        this.payments = payments;
        this.json = json;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = QUEUE, durable = "true",
                    arguments = @Argument(name = "x-dead-letter-exchange", value = "booking.dlx")),
            exchange = @Exchange(name = "booking.events", type = "topic"),
            key = "booking.payment_rejected"))
    public void onPaymentRejected(Message message) {
        Events.handle(message, () -> {
            PaymentRejected rejected = Events.payload(message, PaymentRejected.class, json);
            if (rejected.paymentId() != null) {
                payments.bookingRejectedPayment(Events.id(message), QUEUE, UUID.fromString(rejected.paymentId()),
                        rejected.reason());
            }
        });
    }
}
