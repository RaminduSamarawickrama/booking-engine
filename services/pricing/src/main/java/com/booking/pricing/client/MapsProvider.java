package com.booking.pricing.client;

import com.booking.pricing.domain.Place;
import com.booking.pricing.domain.Route;

import java.util.List;
import java.util.Optional;

/** Geocoding and routing. "mock" works offline; "osm" (OpenStreetMap) comes next. */
public interface MapsProvider {

    List<Place> search(String query, int limit);

    Optional<Place> find(String placeId);

    Route route(Place from, Place to);
}
