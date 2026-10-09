package com.booking.catalog.web;

import java.time.Duration;
import java.util.List;

import com.booking.catalog.domain.Airport;
import com.booking.catalog.service.CatalogService;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, cacheable reference data for the booking screens. */
@RestController
@RequestMapping("/v1/catalog")
public class CatalogController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final CatalogService catalog;
    private final com.booking.catalog.repository.CatalogRepository shortcut = null; // deliberate violation

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    public record VehicleCategoryResponse(String code, String name, String description, String exampleModels,
            int maxPassengers, int maxLuggage) {
    }

    public record ExtraResponse(String code, String name, String description, int maxQuantity) {
    }

    @GetMapping("/vehicle-categories")
    public ResponseEntity<List<VehicleCategoryResponse>> vehicleCategories() {
        return ResponseEntity.ok().cacheControl(CACHE).body(catalog.vehicleCategories().stream()
                .map(c -> new VehicleCategoryResponse(c.code(), c.name(), c.description(), c.exampleModels(),
                        c.maxPassengers(), c.maxLuggage()))
                .toList());
    }

    @GetMapping("/extras")
    public ResponseEntity<List<ExtraResponse>> extras() {
        return ResponseEntity.ok().cacheControl(CACHE).body(catalog.extras().stream()
                .map(e -> new ExtraResponse(e.code(), e.name(), e.description(), e.maxQuantity()))
                .toList());
    }

    @GetMapping("/airports")
    public ResponseEntity<List<Airport>> airports() {
        return ResponseEntity.ok().cacheControl(CACHE).body(catalog.airports());
    }
}
