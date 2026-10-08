package com.booking.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.booking.platform.messaging.DomainEvent;
import com.booking.platform.messaging.EventHeaders;
import com.booking.platform.messaging.Inbox;
import com.booking.platform.messaging.Outbox;
import com.booking.platform.messaging.OutboxRelay;
import com.booking.platform.security.ResourceServerSecurity;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Boots a service made only of the platform library against real Postgres and RabbitMQ,
 * using the same shared configuration file the services import.
 */
@SpringBootTest(properties = {
        "spring.config.import=classpath:booking-platform.yml",
        "DB_SCHEMA=public", "DB_USERNAME=unused", "DB_PASSWORD=unused",
        "booking.platform.security.public-paths=/v1/public/**",
        "booking.platform.messaging.relay-initial-delay=PT1H",
        "management.server.port=",
})
@AutoConfigureMockMvc
@Testcontainers
class PlatformIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    record BookingConfirmed(String bookingId, long amountMinor) implements DomainEvent {
        @Override
        public String type() {
            return "booking.confirmed";
        }

        @Override
        public String aggregateType() {
            return "booking";
        }

        @Override
        public String aggregateId() {
            return bookingId;
        }
    }

    @Autowired MockMvc mvc;
    @Autowired Outbox outbox;
    @Autowired OutboxRelay relay;
    @Autowired Inbox inbox;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcClient jdbc;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired RabbitAdmin admin;
    @Autowired TopicExchange exchange;
    @Autowired JsonMapper json;

    @Test
    void committedEventsArePublishedOnceWithTheirMetadata() {
        Queue queue = new AnonymousQueue();
        admin.declareQueue(queue);
        Binding binding = BindingBuilder.bind(queue).to(exchange).with("booking.*");
        admin.declareBinding(binding);

        UUID id = tx.execute(status -> outbox.append(new BookingConfirmed("BK-42", 12_500)));

        assertThat(relay.relay()).isEqualTo(1);
        assertThat(relay.relay()).isZero();

        Message message = rabbitTemplate.receive(queue.getName(), 5_000);
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(String.valueOf(id));
        assertThat(message.getMessageProperties().getType()).isEqualTo("booking.confirmed");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("booking.confirmed");
        assertThat(message.getMessageProperties().getHeaders()).containsEntry(EventHeaders.AGGREGATE_ID, "BK-42");
        assertThat(message.getMessageProperties().getHeaders().get(EventHeaders.VERSION)).isEqualTo(1);
        JsonNode payload = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        assertThat(payload.get("bookingId").asString()).isEqualTo("BK-42");
        assertThat(payload.get("amountMinor").asLong()).isEqualTo(12_500);
        assertThat(rabbitTemplate.receive(queue.getName(), 500)).isNull();
    }

    @Test
    void rolledBackChangesPublishNothing() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            outbox.append(new BookingConfirmed("BK-rollback", 1));
            throw new IllegalStateException("payment failed");
        })).isInstanceOf(IllegalStateException.class);

        Integer rows = jdbc.sql("select count(*) from outbox_event where aggregate_id = 'BK-rollback'")
                .query(Integer.class).single();
        assertThat(rows).isZero();
    }

    @Test
    void appendingOutsideATransactionFails() {
        assertThatThrownBy(() -> outbox.append(new BookingConfirmed("BK-1", 1)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void inboxAcceptsEachMessageOncePerConsumer() {
        UUID messageId = UUID.randomUUID();
        Boolean first = tx.execute(s -> inbox.firstDelivery(messageId, "notification"));
        Boolean redelivered = tx.execute(s -> inbox.firstDelivery(messageId, "notification"));
        Boolean otherConsumer = tx.execute(s -> inbox.firstDelivery(messageId, "dispatch"));
        assertThat(first).isTrue();
        assertThat(redelivered).isFalse();
        assertThat(otherConsumer).isTrue();
    }

    @Test
    void endpointsNeedATokenUnlessPublic() throws Exception {
        mvc.perform(get("/v1/public/ping")).andExpect(status().isOk()).andExpect(content().string("pong"));
        mvc.perform(get("/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/me").with(jwt().jwt(j -> j.subject("user-7"))))
                .andExpect(status().isOk()).andExpect(content().string("user-7"));
    }

    @Test
    void rolesComeFromTheRolesClaim() throws Exception {
        var customer = jwt().jwt(j -> j.claim("roles", java.util.List.of("CUSTOMER")))
                .authorities(ResourceServerSecurity.rolesConverter());
        var admin = jwt().jwt(j -> j.claim("roles", java.util.List.of("ADMIN")))
                .authorities(ResourceServerSecurity.rolesConverter());

        mvc.perform(get("/v1/admin/ping").with(customer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
        mvc.perform(get("/v1/admin/ping").with(admin)).andExpect(status().isOk());
    }
}
