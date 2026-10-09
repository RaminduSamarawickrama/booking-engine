package com.booking.booking.web;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import com.booking.booking.domain.BookingRepository;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bookings for the operations console, newest first, optionally by status. */
@RestController
@RequestMapping("/v1/admin/bookings")
@PreAuthorize("hasAnyRole('ADMIN', 'DISPATCHER')")
@Validated
public class AdminBookingController {

    private final BookingRepository repository;

    public AdminBookingController(BookingRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<BookingController.BookingView> list(
            @RequestParam(required = false) @Pattern(regexp = "PENDING_PAYMENT|CONFIRMED|CANCELLED|EXPIRED") String status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return repository.findRecent(status, limit, offset).stream().map(BookingController.BookingView::of).toList();
    }
}
