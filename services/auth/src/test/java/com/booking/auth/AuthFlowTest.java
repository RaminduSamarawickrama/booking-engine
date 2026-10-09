package com.booking.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "DB_SCHEMA=public", "DB_USERNAME=unused", "DB_PASSWORD=unused",
        "AUTH_SEED_ADMIN_EMAIL=Admin@Example.test", "AUTH_SEED_ADMIN_PASSWORD=correct-horse-battery",
        "booking.platform.messaging.relay-initial-delay=PT1H",
        "management.server.port=",
})
@AutoConfigureMockMvc
@Testcontainers
class AuthFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcClient jdbc;

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions postJson(String path, String content) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private static String bearer(JsonNode session) {
        return "Bearer " + session.get("accessToken").asString();
    }

    @Test
    void customerRegistersSignsInAndSeesTheirProfile() throws Exception {
        JsonNode registered = body(postJson("/v1/auth/register", """
                {"email":"Ada@Example.test","password":"a long passphrase","fullName":"Ada Lovelace","phone":"+44 20 7946 0000"}
                """).andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("ada@example.test"))
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER")));

        mvc.perform(get("/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(registered)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ada Lovelace"));

        postJson("/v1/auth/login", "{\"email\":\"ADA@example.test\",\"password\":\"a long passphrase\"}")
                .andExpect(status().isOk());
        postJson("/v1/auth/login", "{\"email\":\"ada@example.test\",\"password\":\"wrong password!\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
        postJson("/v1/auth/login", "{\"email\":\"nobody@example.test\",\"password\":\"wrong password!\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));

        postJson("/v1/auth/register", """
                {"email":"ada@example.test","password":"another passphrase","fullName":"Ada Again"}
                """).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("email_taken"));

        Integer events = jdbc.sql("select count(*) from outbox_event where event_type = 'user.registered'")
                .query(Integer.class).single();
        assertThat(events).isGreaterThanOrEqualTo(2); // the seeded admin and Ada
    }

    @Test
    void weakPasswordsAndBadInputAreRejected() throws Exception {
        postJson("/v1/auth/register", "{\"email\":\"bob@example.test\",\"password\":\"short\",\"fullName\":\"Bob\"}")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("password_too_short"));
        postJson("/v1/auth/register", "{\"email\":\"not-an-email\",\"password\":\"a long passphrase\",\"fullName\":\"Bob\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void refreshTokensRotateAndReuseSignsTheSessionOut() throws Exception {
        JsonNode first = body(postJson("/v1/auth/register", """
                {"email":"cy@example.test","password":"a long passphrase","fullName":"Cy"}
                """).andExpect(status().isCreated()));
        String original = first.get("refreshToken").asString();

        JsonNode second = body(postJson("/v1/auth/refresh", "{\"refreshToken\":\"" + original + "\"}")
                .andExpect(status().isOk()));
        String rotated = second.get("refreshToken").asString();
        assertThat(rotated).isNotEqualTo(original);

        // The old token was copied by someone: the whole session is revoked.
        postJson("/v1/auth/refresh", "{\"refreshToken\":\"" + original + "\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh_token_reused"));
        postJson("/v1/auth/refresh", "{\"refreshToken\":\"" + rotated + "\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh_token_reused"));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        JsonNode session = body(postJson("/v1/auth/register", """
                {"email":"dee@example.test","password":"a long passphrase","fullName":"Dee"}
                """));
        String refresh = session.get("refreshToken").asString();
        postJson("/v1/auth/logout", "{\"refreshToken\":\"" + refresh + "\"}").andExpect(status().isNoContent());
        postJson("/v1/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminsManageStaffAccounts() throws Exception {
        JsonNode admin = body(postJson("/v1/auth/login",
                "{\"email\":\"admin@example.test\",\"password\":\"correct-horse-battery\"}").andExpect(status().isOk()));
        JsonNode customer = body(postJson("/v1/auth/register", """
                {"email":"eve@example.test","password":"a long passphrase","fullName":"Eve"}
                """));
        String driver = """
                {"email":"dan@example.test","password":"a long passphrase","fullName":"Dan Driver","roles":["DRIVER"]}
                """;

        mvc.perform(post("/v1/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                .contentType(MediaType.APPLICATION_JSON).content(driver))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(driver))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("DRIVER"));
        mvc.perform(get("/v1/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/v1/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void jwksPublishesOnlyThePublicKey() throws Exception {
        JsonNode keys = body(mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk()));
        JsonNode key = keys.get("keys").get(0);
        assertThat(key.get("kty").asString()).isEqualTo("EC");
        assertThat(key.get("crv").asString()).isEqualTo("P-256");
        assertThat(key.has("d")).isFalse();
    }

    @Test
    void tamperedTokensAreRejected() throws Exception {
        JsonNode session = body(postJson("/v1/auth/register", """
                {"email":"fay@example.test","password":"a long passphrase","fullName":"Fay"}
                """));
        String token = session.get("accessToken").asString();
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        mvc.perform(get("/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }
}
