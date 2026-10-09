package com.booking.payment.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.booking.payment.domain.Payment;
import com.booking.payment.domain.PaymentStatus;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {

    private final JdbcClient jdbc;

    public PaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Payment p) {
        jdbc.sql("""
                insert into payment (id, booking_id, booking_reference, attempt, amount_minor, currency, customer_email,
                                     provider, provider_reference, status, created_at, updated_at)
                values (:id, :booking, :reference, :attempt, :amount, :currency, :email, :provider, :providerRef,
                        :status, :at, :at)
                """)
                .param("id", p.id()).param("booking", p.bookingId()).param("reference", p.bookingReference())
                .param("attempt", p.attempt()).param("amount", p.amountMinor()).param("currency", p.currency())
                .param("email", p.customerEmail()).param("provider", p.provider())
                .param("providerRef", p.providerReference()).param("status", p.status().name())
                .param("at", Timestamp.from(p.createdAt()))
                .update();
    }

    public int nextAttempt(UUID bookingId) {
        return jdbc.sql("select coalesce(max(attempt), 0) + 1 from payment where booking_id = :b")
                .param("b", bookingId).query(Integer.class).single();
    }

    public boolean hasSucceeded(UUID bookingId) {
        return jdbc.sql("select exists (select 1 from payment where booking_id = :b and status = 'SUCCEEDED')")
                .param("b", bookingId).query(Boolean.class).single();
    }

    public Optional<Payment> findById(UUID id) {
        return jdbc.sql("select * from payment where id = :id").param("id", id).query(PaymentRepository::map).optional();
    }

    public Optional<Payment> lockById(UUID id) {
        return jdbc.sql("select * from payment where id = :id for update").param("id", id)
                .query(PaymentRepository::map).optional();
    }

    /** The newest attempt with this provider for the booking, locked. */
    public Optional<Payment> lockLatestFor(UUID bookingId, String provider) {
        return jdbc.sql("""
                select * from payment where booking_id = :b and provider = :provider
                order by attempt desc limit 1 for update
                """).param("b", bookingId).param("provider", provider).query(PaymentRepository::map).optional();
    }

    public Optional<Payment> lockByProviderPaymentId(String providerPaymentId) {
        return jdbc.sql("select * from payment where provider_payment_id = :p for update")
                .param("p", providerPaymentId).query(PaymentRepository::map).optional();
    }

    public void setProviderReference(UUID id, String providerReference, Instant at) {
        jdbc.sql("update payment set provider_reference = :ref, updated_at = :at where id = :id")
                .param("ref", providerReference).param("at", Timestamp.from(at)).param("id", id).update();
    }

    public boolean updateStatus(UUID id, PaymentStatus from, PaymentStatus to, String providerPaymentId,
            String failureCode, String failureMessage, Instant at) {
        return jdbc.sql("""
                update payment set status = :to, updated_at = :at,
                    provider_payment_id = coalesce(:providerPaymentId, provider_payment_id),
                    failure_code = :code, failure_message = :message
                where id = :id and status = :from
                """)
                .param("to", to.name()).param("from", from.name()).param("id", id)
                .param("providerPaymentId", providerPaymentId).param("code", failureCode)
                .param("message", failureMessage).param("at", Timestamp.from(at))
                .update() == 1;
    }

    private static Payment map(ResultSet rs, int row) throws SQLException {
        return new Payment(
                rs.getObject("id", UUID.class),
                rs.getObject("booking_id", UUID.class),
                rs.getString("booking_reference"),
                rs.getInt("attempt"),
                rs.getInt("amount_minor"),
                rs.getString("currency"),
                rs.getString("customer_email"),
                rs.getString("provider"),
                rs.getString("provider_reference"),
                rs.getString("provider_payment_id"),
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getString("failure_code"),
                rs.getString("failure_message"),
                rs.getTimestamp("created_at").toInstant());
    }
}
