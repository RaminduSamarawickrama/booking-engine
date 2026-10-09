package com.booking.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("booking.payments")
public record PaymentProperties(String bookingUri, String returnUrl, String stripePublishableKey,
        String stripeIntegrationIdentifier) {
}
