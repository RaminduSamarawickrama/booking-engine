package com.booking.booking.repository;

import com.booking.booking.domain.Booking;
import com.booking.booking.domain.BookingStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.json.JsonMapper;

@Repository
public class BookingRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public BookingRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insert(Booking b, String manageTokenHash, UUID quoteId) {
        jdbc.sql("""
                insert into booking (id, reference, status, user_id, customer_name, customer_email, customer_phone,
                    pickup, dropoff, pickup_at, passengers, luggage, flight_number, driver_notes, category_code,
                    currency, vehicle_price_minor, extras_total_minor, total_minor, distance_meters, duration_seconds,
                    quote_id, manage_token_hash, created_at, updated_at)
                values (:id, :reference, :status, :userId, :name, :email, :phone,
                    cast(:pickup as jsonb), cast(:dropoff as jsonb), :pickupAt, :passengers, :luggage, :flight, :notes,
                    :category, :currency, :vehicle, :extras, :total, :distance, :duration,
                    :quoteId, :tokenHash, :createdAt, :createdAt)
                """)
                .param("id", b.id()).param("reference", b.reference()).param("status", b.status().name())
                .param("userId", b.userId()).param("name", b.customerName()).param("email", b.customerEmail())
                .param("phone", b.customerPhone())
                .param("pickup", json.writeValueAsString(b.pickup()))
                .param("dropoff", json.writeValueAsString(b.dropoff()))
                .param("pickupAt", Timestamp.from(b.pickupAt()))
                .param("passengers", b.passengers()).param("luggage", b.luggage())
                .param("flight", b.flightNumber()).param("notes", b.driverNotes())
                .param("category", b.categoryCode()).param("currency", b.currency())
                .param("vehicle", b.vehiclePriceMinor()).param("extras", b.extrasTotalMinor())
                .param("total", b.totalMinor()).param("distance", b.distanceMeters())
                .param("duration", b.durationSeconds()).param("quoteId", quoteId)
                .param("tokenHash", manageTokenHash).param("createdAt", Timestamp.from(b.createdAt()))
                .update();
        for (Booking.ExtraLine line : b.extras()) {
            jdbc.sql("insert into booking_extra (booking_id, code, quantity, unit_price_minor) values (:b, :c, :q, :p)")
                    .param("b", b.id()).param("c", line.code()).param("q", line.quantity())
                    .param("p", line.unitPriceMinor()).update();
        }
    }

    public Optional<Booking> findByReference(String reference) {
        return jdbc.sql("select * from booking where reference = :r").param("r", reference)
                .query(this::map).optional().map(this::withExtras);
    }

    public Optional<Booking> lockById(UUID id) {
        return jdbc.sql("select * from booking where id = :id for update").param("id", id)
                .query(this::map).optional().map(this::withExtras);
    }

    public Optional<String> manageTokenHash(String reference) {
        return jdbc.sql("select manage_token_hash from booking where reference = :r").param("r", reference)
                .query(String.class).optional();
    }

    public List<Booking> findByUser(UUID userId, int limit) {
        return jdbc.sql("select * from booking where user_id = :u order by pickup_at desc limit :limit")
                .param("u", userId).param("limit", limit).query(this::map).list().stream().map(this::withExtras).toList();
    }

    public List<Booking> findRecent(String status, int limit, int offset) {
        return jdbc.sql("""
                select * from booking where (cast(:status as text) is null or status = :status)
                order by created_at desc limit :limit offset :offset
                """)
                .param("status", status).param("limit", limit).param("offset", offset)
                .query(this::map).list().stream().map(this::withExtras).toList();
    }

    public boolean updateStatus(UUID id, BookingStatus from, BookingStatus to, String reason, Instant at) {
        int updated = jdbc.sql("update booking set status = :to, updated_at = :at where id = :id and status = :from")
                .param("to", to.name()).param("at", Timestamp.from(at)).param("id", id).param("from", from.name())
                .update();
        if (updated == 1) {
            jdbc.sql("""
                    insert into booking_status_change (booking_id, from_status, to_status, reason, changed_at)
                    values (:id, :from, :to, :reason, :at)
                    """)
                    .param("id", id).param("from", from.name()).param("to", to.name()).param("reason", reason)
                    .param("at", Timestamp.from(at)).update();
        }
        return updated == 1;
    }

    public void recordCreated(UUID id, Instant at) {
        jdbc.sql("""
                insert into booking_status_change (booking_id, from_status, to_status, reason, changed_at)
                values (:id, null, 'PENDING_PAYMENT', 'created', :at)
                """).param("id", id).param("at", Timestamp.from(at)).update();
    }

    /** Unpaid bookings older than the cutoff, locked so parallel instances don't both expire them. */
    public List<UUID> lockExpiredPending(Instant cutoff, int limit) {
        return jdbc.sql("""
                select id from booking where status = 'PENDING_PAYMENT' and created_at < :cutoff
                order by created_at limit :limit for update skip locked
                """).param("cutoff", Timestamp.from(cutoff)).param("limit", limit).query(UUID.class).list();
    }

    public void linkToUser(UUID bookingId, UUID userId) {
        jdbc.sql("update booking set user_id = :u where id = :id and (user_id is null or user_id = :u)")
                .param("u", userId).param("id", bookingId).update();
    }

    private Booking withExtras(Booking b) {
        Map<UUID, List<Booking.ExtraLine>> byBooking = jdbc.sql(
                "select booking_id, code, quantity, unit_price_minor from booking_extra where booking_id = :id order by code")
                .param("id", b.id())
                .query((rs, row) -> Map.entry(rs.getObject(1, UUID.class),
                        new Booking.ExtraLine(rs.getString(2), rs.getInt(3), rs.getInt(4))))
                .list().stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return new Booking(b.id(), b.reference(), b.status(), b.userId(), b.customerName(), b.customerEmail(),
                b.customerPhone(), b.pickup(), b.dropoff(), b.pickupAt(), b.passengers(), b.luggage(),
                b.flightNumber(), b.driverNotes(), b.categoryCode(), b.currency(), b.vehiclePriceMinor(),
                b.extrasTotalMinor(), b.totalMinor(), b.distanceMeters(), b.durationSeconds(), b.createdAt(),
                byBooking.getOrDefault(b.id(), List.of()));
    }

    private Booking map(ResultSet rs, int row) throws SQLException {
        return new Booking(
                rs.getObject("id", UUID.class),
                rs.getString("reference"),
                BookingStatus.valueOf(rs.getString("status")),
                rs.getObject("user_id", UUID.class),
                rs.getString("customer_name"),
                rs.getString("customer_email"),
                rs.getString("customer_phone"),
                json.readTree(rs.getString("pickup")),
                json.readTree(rs.getString("dropoff")),
                rs.getTimestamp("pickup_at").toInstant(),
                rs.getInt("passengers"),
                rs.getInt("luggage"),
                rs.getString("flight_number"),
                rs.getString("driver_notes"),
                rs.getString("category_code"),
                rs.getString("currency"),
                rs.getInt("vehicle_price_minor"),
                rs.getInt("extras_total_minor"),
                rs.getInt("total_minor"),
                rs.getInt("distance_meters"),
                rs.getInt("duration_seconds"),
                rs.getTimestamp("created_at").toInstant(),
                List.of());
    }
}
