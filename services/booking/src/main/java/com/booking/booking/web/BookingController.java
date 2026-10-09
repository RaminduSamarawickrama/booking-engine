package com.booking.booking.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.booking.booking.Bookings;
import com.booking.booking.domain.Booking;

import com.booking.platform.web.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

/**
 * Bookings for guests and signed-in customers alike. A guest proves access with the manage
 * token returned at creation (sent in the {@value #TOKEN_HEADER} header); a signed-in
 * customer with their access token.
 */
@RestController
@RequestMapping("/v1/bookings")
public class BookingController {

    public static final String TOKEN_HEADER = "X-Booking-Token";

    private final Bookings bookings;
    private final IdempotentRequests idempotent;

    public BookingController(Bookings bookings, IdempotentRequests idempotent) {
        this.bookings = bookings;
        this.idempotent = idempotent;
    }

    public record ExtraChoice(@NotBlank @Size(max = 40) String code, @Min(0) @Max(10) int quantity) {
    }

    public record CreateRequest(
            @NotNull UUID quoteId,
            @NotBlank @Size(max = 40) String categoryCode,
            @Size(max = 10) List<@Valid ExtraChoice> extras,
            @NotBlank @Size(max = 120) String customerName,
            @NotBlank @Email @Size(max = 254) String customerEmail,
            @NotBlank @Pattern(regexp = "^\\+?[0-9 ()-]{6,20}$", message = "must be a phone number") String customerPhone,
            @Size(max = 12) String flightNumber,
            @Size(max = 500) String driverNotes) {
    }

    public record ExtraView(String code, int quantity, int unitPriceMinor, int totalMinor) {
    }

    public record BookingView(String reference, String status, String customerName, String customerEmail,
            String customerPhone, JsonNode pickup, JsonNode dropoff, Instant pickupAt, int passengers, int luggage,
            String flightNumber, String driverNotes, String categoryCode, String currency, int vehiclePriceMinor,
            List<ExtraView> extras, int extrasTotalMinor, int totalMinor, int distanceMeters, int durationSeconds,
            boolean linkedToAccount, Instant createdAt) {

        public static BookingView of(Booking b) {
            return new BookingView(b.reference(), b.status().name(), b.customerName(), b.customerEmail(),
                    b.customerPhone(), b.pickup(), b.dropoff(), b.pickupAt(), b.passengers(), b.luggage(),
                    b.flightNumber(), b.driverNotes(), b.categoryCode(), b.currency(), b.vehiclePriceMinor(),
                    b.extras().stream().map(e -> new ExtraView(e.code(), e.quantity(), e.unitPriceMinor(), e.totalMinor()))
                            .toList(),
                    b.extrasTotalMinor(), b.totalMinor(), b.distanceMeters(), b.durationSeconds(), b.userId() != null,
                    b.createdAt());
        }
    }

    /** The manage token is shown once; the guest keeps it in their confirmation link. */
    public record CreatedView(BookingView booking, String manageToken) {
    }

    /**
     * Open to guests. Send an {@code Idempotency-Key} so a retried request returns the first
     * booking instead of creating a second one.
     */
    @PostMapping
    public ResponseEntity<CreatedView> create(@Valid @RequestBody CreateRequest request,
            @RequestHeader(name = IdempotentRequests.HEADER, required = false) String idempotencyKey,
            Authentication auth) {
        UUID user = userId(auth);
        var result = idempotent.run(idempotencyKey, java.util.Arrays.asList(request, user), CreatedView.class, () -> {
            Bookings.Created created = bookings.create(new Bookings.NewBooking(request.quoteId(),
                    request.categoryCode(),
                    request.extras() == null ? List.of()
                            : request.extras().stream().map(e -> new Bookings.ExtraRequest(e.code(), e.quantity())).toList(),
                    request.customerName(), request.customerEmail(), request.customerPhone(), request.flightNumber(),
                    request.driverNotes(), user));
            return new CreatedView(BookingView.of(created.booking()), created.manageToken());
        });
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .location(URI.create("/v1/bookings/" + result.body().booking().reference()))
                .body(result.body());
    }

    @GetMapping("/mine")
    public List<BookingView> mine(Authentication auth) {
        UUID user = userId(auth);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "sign_in_required", "Sign in to see your bookings.");
        }
        return bookings.mine(user).stream().map(BookingView::of).toList();
    }

    /** Saves a guest booking to the signed-in account; needs the booking's manage token. */
    @PostMapping("/{reference}/claim")
    public BookingView claim(@PathVariable String reference,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token, Authentication auth) {
        return BookingView.of(bookings.claim(reference, token, userId(auth)));
    }

    @GetMapping("/{reference}")
    public BookingView get(@PathVariable String reference,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token, Authentication auth) {
        return BookingView.of(bookings.viewable(reference, token, userId(auth), isStaff(auth)));
    }

    @PostMapping("/{reference}/cancel")
    public BookingView cancel(@PathVariable String reference,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token, Authentication auth) {
        return BookingView.of(bookings.cancel(reference, token, userId(auth), isStaff(auth)));
    }

    private static UUID userId(Authentication auth) {
        return auth instanceof JwtAuthenticationToken jwt ? UUID.fromString(jwt.getName()) : null;
    }

    private static boolean isStaff(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_DISPATCHER"));
    }
}
