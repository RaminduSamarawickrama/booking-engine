package com.booking.platform.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code booking.platform}; defaults suit local development. */
@ConfigurationProperties("booking.platform")
public record PlatformProperties(Security security, Messaging messaging) {

    public PlatformProperties {
        security = security == null ? new Security(List.of()) : security;
        messaging = messaging == null ? new Messaging(null, null, 0, null) : messaging;
    }

    /**
     * @param publicPaths request patterns reachable without a token, e.g. {@code /v1/quotes/**}
     */
    public record Security(List<String> publicPaths) {
        public Security {
            publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        }
    }

    /**
     * @param exchange       topic exchange every service publishes domain events to
     * @param relayInterval  how often unpublished outbox rows are sent
     * @param relayBatchSize rows sent per relay pass
     * @param retention      how long published outbox rows and inbox entries are kept
     */
    public record Messaging(String exchange, Duration relayInterval, int relayBatchSize, Duration retention) {
        public Messaging {
            exchange = exchange == null || exchange.isBlank() ? "booking.events" : exchange;
            relayInterval = relayInterval == null ? Duration.ofSeconds(1) : relayInterval;
            relayBatchSize = relayBatchSize <= 0 ? 100 : relayBatchSize;
            retention = retention == null ? Duration.ofDays(7) : retention;
        }
    }
}
