package com.booking.platform.config;

import java.time.Clock;

import com.booking.platform.messaging.Inbox;
import com.booking.platform.messaging.Outbox;
import com.booking.platform.messaging.OutboxRelay;
import com.booking.platform.security.ResourceServerSecurity;
import com.booking.platform.web.ProblemResponses;
import com.booking.platform.web.RequestIdFilter;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the platform into every service that has this library on its classpath. Each piece
 * backs off when the service defines its own bean, so a service can override any of them.
 */
@AutoConfiguration(after = OAuth2ResourceServerAutoConfiguration.class,
        before = OAuth2ResourceServerWebSecurityAutoConfiguration.class,
        afterName = {
                "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
                "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
                "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration",
                "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration" })
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class Web {

        @Bean
        @ConditionalOnMissingBean
        ProblemResponses problemResponses() {
            return new ProblemResponses();
        }

        @Bean
        FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
            FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }

        @Bean
        @ConditionalOnMissingBean(SecurityFilterChain.class)
        SecurityFilterChain platformSecurityFilterChain(HttpSecurity http, PlatformProperties properties)
                throws Exception {
            return ResourceServerSecurity.defaultChain(http, properties);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ JdbcClient.class, RabbitTemplate.class })
    @ConditionalOnProperty(prefix = "booking.platform.messaging", name = "enabled", matchIfMissing = true)
    @EnableScheduling
    static class Messaging {

        @Bean
        TopicExchange bookingEventsExchange(PlatformProperties properties) {
            return new TopicExchange(properties.messaging().exchange(), true, false);
        }

        @Bean
        @ConditionalOnMissingBean
        Outbox outbox(JdbcClient jdbc, JsonMapper json, Clock clock) {
            return new Outbox(jdbc, json, clock);
        }

        @Bean
        @ConditionalOnMissingBean
        Inbox inbox(JdbcClient jdbc) {
            return new Inbox(jdbc);
        }

        @Bean
        @ConditionalOnMissingBean
        OutboxRelay outboxRelay(JdbcClient jdbc, RabbitTemplate rabbit, TransactionTemplate tx,
                PlatformProperties properties, Clock clock) {
            return new OutboxRelay(jdbc, rabbit, tx, properties, clock);
        }
    }
}
