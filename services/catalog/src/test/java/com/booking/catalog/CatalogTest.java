package com.booking.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.platform.security.ResourceServerSecurity;
import com.booking.platform.test.Infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = { "DB_SCHEMA=public", "DB_USERNAME=unused", "DB_PASSWORD=unused",
        "booking.platform.messaging.relay-initial-delay=PT1H", "management.server.port=" })
@AutoConfigureMockMvc
@Import(Infrastructure.class)
class CatalogTest {

    @Autowired MockMvc mvc;

    @Test
    void listsCategoriesInDisplayOrderWithoutSignIn() throws Exception {
        mvc.perform(get("/v1/catalog/vehicle-categories"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=300, public"))
                .andExpect(jsonPath("$[0].code").value("STANDARD"))
                .andExpect(jsonPath("$[?(@.code == 'MINIBUS')].maxPassengers").value(8));
    }

    @Test
    void listsAirportsWithTheirTerminals() throws Exception {
        mvc.perform(get("/v1/catalog/airports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.iata == 'LHR')].terminals.length()").value(4));
    }

    @Test
    void listsExtras() throws Exception {
        mvc.perform(get("/v1/catalog/extras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'MEET_GREET')].maxQuantity").value(1));
    }

    @Test
    void onlyAdminsChangeTheCatalog() throws Exception {
        String body = """
                {"name":"Electric","description":"Zero-emission saloon.","exampleModels":"Tesla Model 3",
                 "maxPassengers":3,"maxLuggage":2,"sortOrder":15,"active":true}
                """;
        mvc.perform(put("/v1/admin/catalog/vehicle-categories/ELECTRIC").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/v1/admin/catalog/vehicle-categories/ELECTRIC").with(role("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/v1/admin/catalog/vehicle-categories/ELECTRIC").with(role("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(get("/v1/catalog/vehicle-categories"))
                .andExpect(jsonPath("$[1].code").value("ELECTRIC"));
        mvc.perform(put("/v1/admin/catalog/vehicle-categories/bad code").with(role("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(422));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor role(String role) {
        return jwt().jwt(j -> j.claim("roles", java.util.List.of(role)))
                .authorities(ResourceServerSecurity.rolesConverter());
    }
}
