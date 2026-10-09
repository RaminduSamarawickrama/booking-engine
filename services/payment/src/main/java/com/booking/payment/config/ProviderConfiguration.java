package com.booking.payment.config;

import java.time.Clock;

import com.booking.payment.client.MockPaymentProvider;
import com.booking.payment.client.PaymentProvider;
import com.booking.payment.client.StripePaymentProvider;
import com.booking.stripe.StripeClients;
import com.booking.stripe.StripeSettings;
import com.booking.stripe.payments.StripePaymentsGateway;
import com.booking.stripe.webhooks.StripeEventTranslator;
import com.stripe.StripeClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the payment provider from PAYMENT_PROVIDER: "mock" (default, nothing is charged) or
 * "stripe" (test-mode keys; live keys are refused unless explicitly allowed).
 */
@Configuration(proxyBeanMethods = false)
public class ProviderConfiguration {

    @Bean
    @ConditionalOnProperty(name = "booking.providers.payment", havingValue = "mock", matchIfMissing = true)
    PaymentProvider mockPaymentProvider() {
        return new MockPaymentProvider();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "booking.providers.payment", havingValue = "stripe")
    static class Stripe {

        @Bean
        StripeSettings stripeSettings(@Value("${booking.payment.stripe.api-key}") String apiKey,
                @Value("${booking.payment.stripe.webhook-secret}") String webhookSecret,
                @Value("${booking.payment.stripe.allow-live-mode:false}") boolean allowLiveMode) {
            return new StripeSettings(apiKey, webhookSecret, allowLiveMode);
        }

        @Bean
        StripeClient stripeClient(StripeSettings settings) {
            return StripeClients.create(settings);
        }

        @Bean
        StripeEventTranslator stripeEventTranslator(StripeClient stripe, StripeSettings settings) {
            return new StripeEventTranslator(stripe, settings.webhookSigningSecret());
        }

        @Bean
        PaymentProvider stripePaymentProvider(StripeClient stripe, PaymentProperties properties, Clock clock) {
            if (properties.stripePublishableKey() == null || !properties.stripePublishableKey().startsWith("pk_")) {
                throw new IllegalStateException("STRIPE_PUBLISHABLE_KEY (pk_test_...) is required with PAYMENT_PROVIDER=stripe");
            }
            StripePaymentsGateway gateway = new StripePaymentsGateway(stripe, properties.stripeIntegrationIdentifier(), clock);
            return new StripePaymentProvider(gateway, properties.stripePublishableKey(), properties.returnUrl());
        }
    }
}
