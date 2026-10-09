package com.booking.payment.web;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.booking.payment.domain.Payment;
import com.booking.payment.service.PaymentService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payments for bookings. Guests pass their booking's manage token in X-Booking-Token,
 * signed-in customers their access token; the amount always comes from the booking.
 */
@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    public record StartRequest(@NotBlank @Size(max = 20) String bookingReference) {
    }

    public record MockCardRequest(@NotBlank @Size(max = 30) String cardNumber) {
    }

    public record PaymentResponse(UUID paymentId, String bookingReference, String status, int amountMinor,
            String currency, String provider, String failureCode, String failureMessage, String clientSecret,
            String publishableKey) {

        static PaymentResponse of(Payment p, String clientSecret, String publishableKey) {
            return new PaymentResponse(p.id(), p.bookingReference(), p.status().name(), p.amountMinor(), p.currency(),
                    p.provider(), p.failureCode(), p.failureMessage(), clientSecret, publishableKey);
        }
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> start(@Valid @RequestBody StartRequest request,
            @RequestHeader(name = "X-Booking-Token", required = false) String manageToken,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        PaymentService.Started started = payments.start(request.bookingReference(), manageToken, authorization);
        return ResponseEntity.created(URI.create("/v1/payments/" + started.payment().id()))
                .body(PaymentResponse.of(started.payment(), started.clientSecret(), started.publishableKey()));
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse get(@PathVariable UUID paymentId) {
        return PaymentResponse.of(payments.get(paymentId), null, null);
    }

    /** Mock provider only: "charges" a test card. */
    @PostMapping("/{paymentId}/mock-confirm")
    public PaymentResponse confirmMock(@PathVariable UUID paymentId, @Valid @RequestBody MockCardRequest request) {
        return PaymentResponse.of(payments.confirmMock(paymentId, request.cardNumber()), null, null);
    }
}
