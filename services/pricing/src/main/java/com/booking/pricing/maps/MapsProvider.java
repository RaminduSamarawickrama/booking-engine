package com.booking.pricing.maps;

import java.util.List;
import java.util.Optional;

/** Geocoding and routing. "mock" works offline; "osm" (OpenStreetMap) comes next. */
public interface MapsProvider {

    List<Place> search(String query, int limit);

    Optional<Place> find(String placeId);

    Route route(Place from, Place to);
}
