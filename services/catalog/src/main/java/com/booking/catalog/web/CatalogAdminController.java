package com.booking.catalog.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.booking.catalog.domain.Extra;
import com.booking.catalog.domain.VehicleCategory;
import com.booking.catalog.service.CatalogService;

import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/v1/admin/catalog")
@PreAuthorize("hasRole('ADMIN')")
public class CatalogAdminController {

    private final CatalogService catalog;

    public CatalogAdminController(CatalogService catalog) {
        this.catalog = catalog;
    }

    public record VehicleCategoryRequest(
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Size(max = 300) String description,
            @NotBlank @Size(max = 200) String exampleModels,
            @Min(1) @Max(16) int maxPassengers,
            @Min(0) @Max(30) int maxLuggage,
            @Min(0) int sortOrder,
            boolean active) {
    }

    public record ExtraRequest(
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Size(max = 300) String description,
            @Min(1) @Max(10) int maxQuantity,
            @Min(0) int sortOrder,
            boolean active) {
    }

    @PutMapping("/vehicle-categories/{code}")
    public VehicleCategory putCategory(@PathVariable String code, @Valid @RequestBody VehicleCategoryRequest body) {
        return catalog.save(new VehicleCategory(code, body.name(), body.description(), body.exampleModels(),
                body.maxPassengers(), body.maxLuggage(), body.sortOrder(), body.active()));
    }

    @PutMapping("/extras/{code}")
    public Extra putExtra(@PathVariable String code, @Valid @RequestBody ExtraRequest body) {
        return catalog.save(new Extra(code, body.name(), body.description(), body.maxQuantity(), body.sortOrder(),
                body.active()));
    }
}
