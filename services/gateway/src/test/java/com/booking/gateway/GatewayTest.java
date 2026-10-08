package com.booking.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "CORS_ALLOWED_ORIGIN_PATTERNS=https://booking-customer-web*.vercel.app,http://localhost:*",
        "booking.gateway.rate-limit.sign-in-per-minute=3",
        "management.server.port=",
})
@AutoConfigureMockMvc
class GatewayTest {

    static final AtomicReference<String> seenRequestId = new AtomicReference<>();
    static final HttpServer upstream = startUpstream();

    static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                seenRequestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
                byte[] body = ("{\"path\":\"" + exchange.getRequestURI().getPath() + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("AUTH_URI", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        upstream.stop(0);
    }

    @Autowired MockMvc mvc;

    @Test
    void routesAuthCallsAndPassesARequestId() throws Exception {
        mvc.perform(get("/v1/auth/me").header("X-Request-Id", "client-supplied-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("/v1/auth/me"))
                .andExpect(header().string("X-Request-Id", "client-supplied-1"));
        assertThat(seenRequestId.get()).isEqualTo("client-supplied-1");

        mvc.perform(get("/.well-known/jwks.json").header("X-Request-Id", "bad id\r\nX-Evil: 1"))
                .andExpect(status().isOk());
        assertThat(seenRequestId.get()).hasSize(36).doesNotContain("Evil");
    }

    @Test
    void answersHealthItself() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void allowsOnlyConfiguredOrigins() throws Exception {
        mvc.perform(options("/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://booking-customer-web-git-main.vercel.app")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://booking-customer-web-git-main.vercel.app"));
        mvc.perform(options("/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void slowsDownRepeatedSignIns() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/v1/auth/login").with(r -> { r.setRemoteAddr("203.0.113.9"); return r; })
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        }
        mvc.perform(post("/v1/auth/login").with(r -> { r.setRemoteAddr("203.0.113.9"); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("rate_limited"));
        // Other clients are unaffected.
        mvc.perform(post("/v1/auth/login").with(r -> { r.setRemoteAddr("203.0.113.10"); return r; })
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }
}
