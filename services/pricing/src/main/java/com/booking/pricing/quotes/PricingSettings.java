package com.booking.pricing.quotes;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("booking.pricing")
public record PricingSettings(String currency, ZoneId zone, Duration quoteValidity, Duration minimumLeadTime,
        Duration maximumAdvance) {
}
