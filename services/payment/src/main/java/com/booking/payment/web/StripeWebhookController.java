package com.booking.payment.web;

import com.booking.payment.service.StripeWebhookService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Stripe's webhook endpoint. The raw body is passed on untouched for signature checking. */
@RestController
public class StripeWebhookController {

    private final StripeWebhookService webhooks;

    public StripeWebhookController(StripeWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping(path = "/v1/payments/webhooks/stripe", consumes = "application/json")
    public ResponseEntity<Void> receive(@RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        webhooks.handle(payload, signature == null ? "" : signature);
        return ResponseEntity.ok().build();
    }
}
