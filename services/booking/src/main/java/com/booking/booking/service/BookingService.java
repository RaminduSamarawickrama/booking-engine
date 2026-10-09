package com.booking.booking.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.booking.booking.client.CatalogClient;
import com.booking.booking.client.PricingClient;
import com.booking.booking.domain.Booking;
import com.booking.booking.repository.BookingRepository;
import com.booking.booking.domain.BookingStatus;
import com.booking.booking.domain.FlightNumbers;
import com.booking.booking.domain.References;
import com.booking.booking.service.event.BookingEvent;
import com.booking.platform.messaging.Inbox;
import com.booking.platform.messaging.Outbox;
import com.booking.platform.error.ApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Creating bookings from quotes, guest access, and the payment-driven status changes. */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository repository;
    private final PricingClient pricing;
    private final CatalogClient catalog;
    private final Outbox outbox;
    private final Inbox inbox;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration paymentWindow;
    private final Duration quoteGrace;

    public BookingService(BookingRepository repository, PricingClient pricing, CatalogClient catalog, Outbox outbox, Inbox inbox,
            TransactionTemplate tx, Clock clock,
            @Value("${booking.booking.payment-window}") Duration paymentWindow,
            @Value("${booking.booking.quote-grace}") Duration quoteGrace) {
        this.repository = repository;
        this.pricing = pricing;
        this.catalog = catalog;
        this.outbox = outbox;
        this.inbox = inbox;
        this.tx = tx;
        this.clock = clock;
        this.paymentWindow = paymentWindow;
        this.quoteGrace = quoteGrace;
    }

    public record ExtraRequest(String code, int quantity) {
    }

    public record NewBooking(UUID quoteId, String categoryCode, List<ExtraRequest> extras, String customerName,
            String customerEmail, String customerPhone, String flightNumber, String driverNotes, UUID userId) {
    }

    public record Created(Booking booking, String manageToken) {
    }

    /** Prices come only from the stored quote; the client sends choices, never amounts. */
    public Created create(NewBooking request) {
        PricingClient.Quote quote = pricing.quote(request.quoteId())
                .orElseThrow(() -> ApiException.unprocessable("quote_not_found", "That price is no longer available. Search again."));
        Instant now = clock.instant();
        if (quote.expiresAt().plus(quoteGrace).isBefore(now)) {
            throw ApiException.conflict("quote_expired", "Prices are held for 30 minutes. Search again for a fresh price.");
        }
        PricingClient.Option option = quote.options().stream()
                .filter(o -> o.categoryCode().equals(request.categoryCode()))
                .findFirst()
                .orElseThrow(() -> ApiException.unprocessable("vehicle_not_offered",
                        "That vehicle isn't available for this journey."));

        List<Booking.ExtraLine> extras = priceExtras(request.extras(), quote);
        String flight = null;
        if (request.flightNumber() != null && !request.flightNumber().isBlank()) {
            flight = FlightNumbers.normalise(request.flightNumber()).orElseThrow(() -> ApiException.unprocessable(
                    "invalid_flight_number", "Enter a flight number like BA117."));
        } else if (quote.pickupIsAirport()) {
            throw ApiException.unprocessable("flight_number_required",
                    "Add your flight number so your driver can follow it and wait if it is late.");
        }
        int extrasTotal = extras.stream().mapToInt(Booking.ExtraLine::totalMinor).sum();

        String manageToken = newToken();
        Instant created = now.truncatedTo(ChronoUnit.MICROS);
        for (int attempt = 0; ; attempt++) {
            Booking booking = new Booking(UUID.randomUUID(), References.next(), BookingStatus.PENDING_PAYMENT,
                    request.userId(), request.customerName().strip(),
                    request.customerEmail().strip().toLowerCase(Locale.ROOT), request.customerPhone().strip(),
                    quote.pickup(), quote.dropoff(), quote.pickupAt().toInstant(), quote.passengers(), quote.luggage(),
                    flight, blankToNull(request.driverNotes()), option.categoryCode(), quote.currency(),
                    option.priceMinor(), extrasTotal, option.priceMinor() + extrasTotal, quote.distanceMeters(),
                    quote.durationSeconds(), created, extras);
            try {
                tx.executeWithoutResult(status -> {
                    repository.insert(booking, hash(manageToken), quote.id());
                    repository.recordCreated(booking.id(), created);
                    outbox.append(event("booking.created", booking, null, null));
                });
                return new Created(booking, manageToken);
            } catch (DuplicateKeyException e) {
                if (attempt >= 3) {
                    throw e;
                }
                log.info("Booking reference collision, retrying");
            }
        }
    }

    private List<Booking.ExtraLine> priceExtras(List<ExtraRequest> requested, PricingClient.Quote quote) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        Map<String, CatalogClient.Extra> limits = catalog.extras();
        List<Booking.ExtraLine> lines = new ArrayList<>();
        for (ExtraRequest extra : requested) {
            if (extra.quantity() == 0) {
                continue;
            }
            CatalogClient.Extra limit = limits.get(extra.code());
            Optional<PricingClient.ExtraPrice> price = quote.extras().stream()
                    .filter(p -> p.code().equals(extra.code())).findFirst();
            if (limit == null || price.isEmpty()) {
                throw ApiException.unprocessable("unknown_extra", "We don't offer '" + extra.code() + "'.");
            }
            if (extra.quantity() < 0 || extra.quantity() > limit.maxQuantity()) {
                throw ApiException.unprocessable("too_many_extras",
                        "Up to " + limit.maxQuantity() + " × " + limit.name().toLowerCase(Locale.ROOT) + " per booking.");
            }
            if (lines.stream().anyMatch(l -> l.code().equals(extra.code()))) {
                throw ApiException.unprocessable("duplicate_extra", "List each extra once with its quantity.");
            }
            lines.add(new Booking.ExtraLine(extra.code(), extra.quantity(), price.get().priceMinor()));
        }
        return lines;
    }

    /**
     * Finds a booking the caller may see: with its manage token (guests), as its owner, or as
     * operations staff. Anyone else gets "not found", so references can't be probed.
     */
    public Booking viewable(String reference, String manageToken, UUID userId, boolean staff) {
        String ref = References.normalise(reference);
        Optional<Booking> booking = repository.findByReference(ref);
        boolean allowed = booking.isPresent() && (staff
                || (userId != null && userId.equals(booking.get().userId()))
                || (manageToken != null && repository.manageTokenHash(ref)
                        .map(stored -> MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII),
                                hash(manageToken).getBytes(StandardCharsets.US_ASCII)))
                        .orElse(false)));
        if (!allowed) {
            throw ApiException.notFound("booking_not_found", "We couldn't find that booking. Check the reference and link.");
        }
        return booking.get();
    }

    @Transactional(readOnly = true)
    public List<Booking> recent(String status, int limit, int offset) {
        return repository.findRecent(status, limit, offset);
    }

    @Transactional(readOnly = true)
    public List<Booking> mine(UUID userId) {
        return repository.findByUser(userId, 100);
    }

    /** Customers can cancel before paying; cancelling a paid booking needs the refund policy first. */
    @Transactional
    public Booking cancel(String reference, String manageToken, UUID userId, boolean staff) {
        Booking booking = viewable(reference, manageToken, userId, staff);
        Booking locked = repository.lockById(booking.id()).orElseThrow();
        if (locked.status() != BookingStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("cannot_cancel",
                    locked.status() == BookingStatus.CONFIRMED
                            ? "Paid bookings are cancelled through support while refunds are being set up."
                            : "This booking is already " + locked.status().name().toLowerCase(Locale.ROOT) + ".");
        }
        move(locked, BookingStatus.CANCELLED, "cancelled by customer", null);
        return repository.findByReference(locked.reference()).orElseThrow();
    }

    /**
     * Applies a payment.succeeded event once: the inbox entry and the status change commit
     * together, so a redelivered event is skipped.
     */
    @Transactional
    public void paymentSucceeded(UUID eventId, String consumer, UUID bookingId, String paymentId, int amountMinor,
            String currency) {
        if (inbox.firstDelivery(eventId, consumer)) {
            paymentSucceeded(bookingId, paymentId, amountMinor, currency);
        }
    }

    /** The status change itself; also guarded by the status check. */
    @Transactional
    public void paymentSucceeded(UUID bookingId, String paymentId, int amountMinor, String currency) {
        Optional<Booking> found = repository.lockById(bookingId);
        if (found.isEmpty()) {
            log.warn("Payment {} for unknown booking {}", paymentId, bookingId);
            return;
        }
        Booking booking = found.get();
        if (booking.status() == BookingStatus.CONFIRMED) {
            return;
        }
        if (booking.status() != BookingStatus.PENDING_PAYMENT) {
            outbox.append(event("booking.payment_rejected", booking, paymentId,
                    "booking is " + booking.status().name().toLowerCase(Locale.ROOT)));
            return;
        }
        if (amountMinor != booking.totalMinor() || !booking.currency().equals(currency)) {
            log.error("Payment {} amount {} {} does not match booking {} total {} {}", paymentId, amountMinor, currency,
                    booking.reference(), booking.totalMinor(), booking.currency());
            outbox.append(event("booking.payment_rejected", booking, paymentId, "amount mismatch"));
            return;
        }
        move(booking, BookingStatus.CONFIRMED, "payment " + paymentId, paymentId);
    }

    /**
     * Links a guest booking to the signed-in customer. Holding the manage token proves it is
     * theirs; matching emails would not, because account emails are not verified yet.
     */
    @Transactional
    public Booking claim(String reference, String manageToken, UUID userId) {
        if (userId == null || manageToken == null) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "sign_in_required",
                    "Sign in and open the booking from its confirmation link to save it to your account.");
        }
        Booking booking = viewable(reference, manageToken, null, false);
        if (booking.userId() != null && !booking.userId().equals(userId)) {
            throw ApiException.conflict("already_claimed", "This booking belongs to another account.");
        }
        repository.linkToUser(booking.id(), userId);
        return repository.findByReference(booking.reference()).orElseThrow();
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void expireUnpaid() {
        Integer expired = tx.execute(status -> {
            List<UUID> ids = repository.lockExpiredPending(clock.instant().minus(paymentWindow), 200);
            ids.forEach(id -> repository.lockById(id).ifPresent(b -> move(b, BookingStatus.EXPIRED, "not paid in time", null)));
            return ids.size();
        });
        if (expired != null && expired > 0) {
            log.info("Expired {} unpaid bookings", expired);
        }
    }

    private void move(Booking booking, BookingStatus to, String reason, String paymentId) {
        if (!booking.status().canMoveTo(to)) {
            throw new IllegalStateException(booking.status() + " -> " + to + " is not allowed");
        }
        if (repository.updateStatus(booking.id(), booking.status(), to, reason, clock.instant())) {
            Booking moved = repository.findByReference(booking.reference()).orElseThrow();
            outbox.append(event("booking." + to.name().toLowerCase(Locale.ROOT), moved, paymentId, reason));
        }
    }

    private static BookingEvent event(String type, Booking b, String paymentId, String reason) {
        return new BookingEvent(type, b.id().toString(), b.reference(), b.status().name(), b.customerName(),
                b.customerEmail(), b.userId() == null ? null : b.userId().toString(), b.pickupAt(), b.totalMinor(),
                b.currency(), paymentId, reason);
    }

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
