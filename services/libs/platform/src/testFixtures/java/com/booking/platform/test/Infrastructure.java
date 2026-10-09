package com.booking.platform.test;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres (PostGIS image, as in Compose) and RabbitMQ for a service's integration tests.
 * Import it with {@code @Import(Infrastructure.class)}; Spring caches the context, so the
 * containers start once per test JVM.
 */
@TestConfiguration(proxyBeanMethods = false)
public class Infrastructure {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(
                DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return new RabbitMQContainer("rabbitmq:4.1-management-alpine");
    }

    /** Properties every service test needs; add your own after these. */
    public static final String[] PROPERTIES = {
            "DB_SCHEMA=public", "DB_USERNAME=unused", "DB_PASSWORD=unused",
            "booking.platform.messaging.relay-initial-delay=PT1H",
            "management.server.port=",
    };
}
