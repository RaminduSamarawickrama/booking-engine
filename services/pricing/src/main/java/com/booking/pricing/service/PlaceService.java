package com.booking.pricing.service;

import java.util.List;

import com.booking.pricing.client.MapsProvider;
import com.booking.pricing.domain.Place;

import org.springframework.stereotype.Service;

/** Address suggestions while the customer types. */
@Service
public class PlaceService {

    private final MapsProvider maps;

    public PlaceService(MapsProvider maps) {
        this.maps = maps;
    }

    public List<Place> search(String query, int limit) {
        return maps.search(query, limit);
    }
}
