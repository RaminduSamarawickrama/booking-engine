package com.booking.platform.web;

import com.booking.platform.error.ApiException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ProblemResponsesTest {

    @RestController
    static class Endpoints {

        record Passenger(@NotBlank String name) {
        }

        @GetMapping("/missing")
        String missing() {
            throw ApiException.notFound("booking_not_found", "No booking BK-1.");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("database password is hunter2");
        }

        @GetMapping("/constraint")
        String constraint() {
            throw new jakarta.validation.ConstraintViolationException("limit: must be at most 200", java.util.Set.of());
        }

        @PostMapping("/passengers")
        String create(@Valid @RequestBody Passenger passenger) {
            return passenger.name();
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Endpoints())
                .setControllerAdvice(new ProblemResponses())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void apiExceptionsKeepTheirStatusAndCode() throws Exception {
        mvc.perform(get("/missing").header(RequestIds.HEADER, "req-12345678"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(RequestIds.HEADER, "req-12345678"))
                .andExpect(jsonPath("$.code").value("booking_not_found"))
                .andExpect(jsonPath("$.type").value("urn:booking:problem:booking_not_found"))
                .andExpect(jsonPath("$.detail").value("No booking BK-1."))
                .andExpect(jsonPath("$.requestId").value("req-12345678"));
    }

    @Test
    void unexpectedErrorsHideTheirDetails() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal_error"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("hunter2"))))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void validationErrorsListTheFields() throws Exception {
        mvc.perform(post("/passengers").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void parameterViolationsAreBadRequests() throws Exception {
        mvc.perform(get("/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void frameworkErrorsGainACode() throws Exception {
        mvc.perform(post("/passengers").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));
    }
}
