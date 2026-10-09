package com.booking.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admins add or change what customers can book. Upserts by code, so the same call creates
 * or edits; setting {@code active=false} hides an item without breaking old bookings.
 */
@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping("/v1/admin/catalog")
@PreAuthorize("hasRole('ADMIN')")
public class CatalogAdminController {

    private static final String CODE = "^[A-Z][A-Z0-9_]{1,39}$";

    private final JdbcClient jdbc;

    public CatalogAdminController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record VehicleCategoryUpsert(
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Size(max = 300) String description,
            @NotBlank @Size(max = 200) String exampleModels,
            @Min(1) @Max(16) int maxPassengers,
            @Min(0) @Max(30) int maxLuggage,
            @Min(0) int sortOrder,
            boolean active) {
    }

    public record ExtraUpsert(
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Size(max = 300) String description,
            @Min(1) @Max(10) int maxQuantity,
            @Min(0) int sortOrder,
            boolean active) {
    }

    @PutMapping("/vehicle-categories/{code}")
    @Transactional
    public VehicleCategoryUpsert putCategory(@PathVariable @Pattern(regexp = CODE) String code,
            @Valid @RequestBody VehicleCategoryUpsert body) {
        jdbc.sql("""
                insert into vehicle_category (code, name, description, example_models, max_passengers, max_luggage,
                                              sort_order, active)
                values (:code, :name, :description, :models, :passengers, :luggage, :sort, :active)
                on conflict (code) do update set name = excluded.name, description = excluded.description,
                    example_models = excluded.example_models, max_passengers = excluded.max_passengers,
                    max_luggage = excluded.max_luggage, sort_order = excluded.sort_order, active = excluded.active
                """)
                .param("code", code).param("name", body.name()).param("description", body.description())
                .param("models", body.exampleModels()).param("passengers", body.maxPassengers())
                .param("luggage", body.maxLuggage()).param("sort", body.sortOrder()).param("active", body.active())
                .update();
        return body;
    }

    @PutMapping("/extras/{code}")
    @Transactional
    public ExtraUpsert putExtra(@PathVariable @Pattern(regexp = CODE) String code, @Valid @RequestBody ExtraUpsert body) {
        jdbc.sql("""
                insert into extra (code, name, description, max_quantity, sort_order, active)
                values (:code, :name, :description, :max, :sort, :active)
                on conflict (code) do update set name = excluded.name, description = excluded.description,
                    max_quantity = excluded.max_quantity, sort_order = excluded.sort_order, active = excluded.active
                """)
                .param("code", code).param("name", body.name()).param("description", body.description())
                .param("max", body.maxQuantity()).param("sort", body.sortOrder()).param("active", body.active())
                .update();
        return body;
    }
}
