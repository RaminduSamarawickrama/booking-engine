package com.booking.pricing.web;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import com.booking.pricing.domain.Place;
import com.booking.pricing.service.PlaceService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlaceController {

    private final PlaceService places;

    public PlaceController(PlaceService places) {
        this.places = places;
    }

    /** Address suggestions as the customer types. */
    @GetMapping("/v1/places")
    public List<Place> search(@RequestParam @Size(min = 2, max = 100) String q,
            @RequestParam(defaultValue = "8") @Min(1) @Max(20) int limit) {
        return places.search(q, limit);
    }
}
