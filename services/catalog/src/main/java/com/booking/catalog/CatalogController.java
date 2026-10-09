package com.booking.catalog;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, cacheable reference data for the booking screens. */
@RestController
@RequestMapping("/v1/catalog")
public class CatalogController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final JdbcClient jdbc;

    public CatalogController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record VehicleCategory(String code, String name, String description, String exampleModels,
            int maxPassengers, int maxLuggage) {
    }

    public record Extra(String code, String name, String description, int maxQuantity) {
    }

    public record Terminal(String code, String name, double latitude, double longitude) {
    }

    public record Airport(String iata, String name, String city, double latitude, double longitude,
            List<Terminal> terminals) {
    }

    record TerminalRow(String airportIata, String code, String name, double latitude, double longitude) {
    }

    @GetMapping("/vehicle-categories")
    public ResponseEntity<List<VehicleCategory>> vehicleCategories() {
        return ResponseEntity.ok().cacheControl(CACHE).body(jdbc.sql("""
                select code, name, description, example_models, max_passengers, max_luggage
                from vehicle_category where active order by sort_order
                """).query(VehicleCategory.class).list());
    }

    @GetMapping("/extras")
    public ResponseEntity<List<Extra>> extras() {
        return ResponseEntity.ok().cacheControl(CACHE).body(jdbc.sql("""
                select code, name, description, max_quantity from extra where active order by sort_order
                """).query(Extra.class).list());
    }

    @GetMapping("/airports")
    public ResponseEntity<List<Airport>> airports() {
        Map<String, List<Terminal>> terminals = jdbc.sql("""
                select airport_iata, code, name, latitude, longitude from terminal order by airport_iata, code
                """).query(TerminalRow.class).list().stream()
                .collect(Collectors.groupingBy(TerminalRow::airportIata,
                        Collectors.mapping(t -> new Terminal(t.code(), t.name(), t.latitude(), t.longitude()),
                                Collectors.toList())));
        List<Airport> airports = jdbc.sql("""
                select iata, name, city, latitude, longitude from airport where active order by name
                """).query((rs, row) -> new Airport(rs.getString("iata"), rs.getString("name"), rs.getString("city"),
                        rs.getDouble("latitude"), rs.getDouble("longitude"),
                        terminals.getOrDefault(rs.getString("iata"), List.of()))).list();
        return ResponseEntity.ok().cacheControl(CACHE).body(airports);
    }
}
