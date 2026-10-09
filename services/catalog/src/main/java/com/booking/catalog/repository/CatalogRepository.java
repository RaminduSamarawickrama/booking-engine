package com.booking.catalog.repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.booking.catalog.domain.Airport;
import com.booking.catalog.domain.Extra;
import com.booking.catalog.domain.VehicleCategory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {

    private final JdbcClient jdbc;

    public CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<VehicleCategory> activeCategories() {
        return jdbc.sql("""
                select code, name, description, example_models, max_passengers, max_luggage, sort_order, active
                from vehicle_category where active order by sort_order, code
                """).query(VehicleCategory.class).list();
    }

    public List<Extra> activeExtras() {
        return jdbc.sql("""
                select code, name, description, max_quantity, sort_order, active from extra where active
                order by sort_order, code
                """).query(Extra.class).list();
    }

    record TerminalRow(String airportIata, String code, String name, double latitude, double longitude) {
    }

    public List<Airport> activeAirports() {
        Map<String, List<Airport.Terminal>> terminals = jdbc.sql("""
                select airport_iata, code, name, latitude, longitude from terminal order by airport_iata, code
                """).query(TerminalRow.class).list().stream()
                .collect(Collectors.groupingBy(TerminalRow::airportIata,
                        Collectors.mapping(t -> new Airport.Terminal(t.code(), t.name(), t.latitude(), t.longitude()),
                                Collectors.toList())));
        return jdbc.sql("select iata, name, city, latitude, longitude from airport where active order by name")
                .query((rs, row) -> new Airport(rs.getString("iata"), rs.getString("name"), rs.getString("city"),
                        rs.getDouble("latitude"), rs.getDouble("longitude"),
                        terminals.getOrDefault(rs.getString("iata"), List.of())))
                .list();
    }

    public void upsert(VehicleCategory c) {
        jdbc.sql("""
                insert into vehicle_category (code, name, description, example_models, max_passengers, max_luggage,
                                              sort_order, active)
                values (:code, :name, :description, :models, :passengers, :luggage, :sort, :active)
                on conflict (code) do update set name = excluded.name, description = excluded.description,
                    example_models = excluded.example_models, max_passengers = excluded.max_passengers,
                    max_luggage = excluded.max_luggage, sort_order = excluded.sort_order, active = excluded.active
                """)
                .param("code", c.code()).param("name", c.name()).param("description", c.description())
                .param("models", c.exampleModels()).param("passengers", c.maxPassengers())
                .param("luggage", c.maxLuggage()).param("sort", c.sortOrder()).param("active", c.active())
                .update();
    }

    public void upsert(Extra e) {
        jdbc.sql("""
                insert into extra (code, name, description, max_quantity, sort_order, active)
                values (:code, :name, :description, :max, :sort, :active)
                on conflict (code) do update set name = excluded.name, description = excluded.description,
                    max_quantity = excluded.max_quantity, sort_order = excluded.sort_order, active = excluded.active
                """)
                .param("code", e.code()).param("name", e.name()).param("description", e.description())
                .param("max", e.maxQuantity()).param("sort", e.sortOrder()).param("active", e.active())
                .update();
    }
}
