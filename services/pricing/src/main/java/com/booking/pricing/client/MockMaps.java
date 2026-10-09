package com.booking.pricing.client;

import com.booking.pricing.domain.Place;
import com.booking.pricing.domain.Route;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Offline maps for development and demos: London airport terminals and well-known places,
 * with road distance estimated as 1.3 times the straight-line distance. No network calls,
 * no API keys, no cost.
 */
public class MockMaps implements MapsProvider {

    static final double ROAD_FACTOR = 1.3;

    private static final List<Place> PLACES = List.of(
            airport("LHR-T2", "Heathrow Terminal 2", "Heathrow Airport, Hounslow TW6 1EW", 51.4697, -0.4513, "LHR", "T2"),
            airport("LHR-T3", "Heathrow Terminal 3", "Heathrow Airport, Hounslow TW6 1QG", 51.4711, -0.4568, "LHR", "T3"),
            airport("LHR-T4", "Heathrow Terminal 4", "Heathrow Airport, Hounslow TW6 3XA", 51.4588, -0.4466, "LHR", "T4"),
            airport("LHR-T5", "Heathrow Terminal 5", "Heathrow Airport, Hounslow TW6 2GA", 51.4723, -0.4877, "LHR", "T5"),
            airport("LGW-N", "Gatwick North Terminal", "Gatwick Airport, Crawley RH6 0PJ", 51.1618, -0.1774, "LGW", "N"),
            airport("LGW-S", "Gatwick South Terminal", "Gatwick Airport, Crawley RH6 0NP", 51.1564, -0.1609, "LGW", "S"),
            airport("STN", "Stansted Airport", "Bassingbourn Road, Stansted CM24 1QW", 51.8899, 0.2627, "STN", "MAIN"),
            airport("LTN", "Luton Airport", "Airport Way, Luton LU2 9LY", 51.8790, -0.3760, "LTN", "MAIN"),
            airport("LCY", "London City Airport", "Hartmann Road, London E16 2PX", 51.5033, 0.0553, "LCY", "MAIN"),
            place("kings-cross", "King's Cross Station", "Euston Road, London N1 9AL", 51.5320, -0.1233),
            place("paddington", "Paddington Station", "Praed Street, London W2 1HU", 51.5154, -0.1755),
            place("victoria", "Victoria Station", "Victoria Street, London SW1V 1JU", 51.4952, -0.1441),
            place("waterloo", "Waterloo Station", "Waterloo Road, London SE1 8SW", 51.5031, -0.1132),
            place("liverpool-street", "Liverpool Street Station", "Liverpool Street, London EC2M 7PY", 51.5178, -0.0823),
            place("canary-wharf", "Canary Wharf", "Canada Square, London E14 5AB", 51.5054, -0.0235),
            place("the-shard", "The Shard", "32 London Bridge Street, London SE1 9SG", 51.5045, -0.0865),
            place("oxford-circus", "Oxford Circus", "Oxford Street, London W1B 3AG", 51.5152, -0.1419),
            place("covent-garden", "Covent Garden", "The Piazza, London WC2E 8RF", 51.5117, -0.1240),
            place("shoreditch", "Shoreditch High Street", "Shoreditch High Street, London E1 6PJ", 51.5233, -0.0754),
            place("camden", "Camden Town", "Camden High Street, London NW1 7JE", 51.5390, -0.1426),
            place("greenwich", "Greenwich", "Cutty Sark Gardens, London SE10 9HT", 51.4826, -0.0077),
            place("wembley", "Wembley Stadium", "Wembley, London HA9 0WS", 51.5560, -0.2796),
            place("richmond", "Richmond", "Richmond upon Thames TW9 1DN", 51.4613, -0.3037),
            place("croydon", "East Croydon Station", "George Street, Croydon CR0 1LF", 51.3755, -0.0927),
            place("stratford", "Stratford (Westfield)", "Montfichet Road, London E20 1EJ", 51.5432, -0.0063),
            place("windsor", "Windsor Castle", "Windsor SL4 1NJ", 51.4839, -0.6044),
            place("oxford", "Oxford city centre", "Carfax, Oxford OX1 1ET", 51.7520, -1.2577),
            place("cambridge", "Cambridge city centre", "Market Hill, Cambridge CB2 3NJ", 52.2053, 0.1218),
            place("brighton", "Brighton seafront", "Brighton Palace Pier, Brighton BN2 1TW", 50.8167, -0.1371),
            place("reading", "Reading Station", "Station Hill, Reading RG1 1LZ", 51.4588, -0.9718));

    private static Place airport(String id, String name, String address, double lat, double lon, String iata,
            String terminal) {
        return new Place(id, name, address, lat, lon, iata, terminal);
    }

    private static Place place(String id, String name, String address, double lat, double lon) {
        return new Place(id, name, address, lat, lon, null, null);
    }

    @Override
    public List<Place> search(String query, int limit) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        if (q.length() < 2) {
            return List.of();
        }
        String[] words = q.split("\\s+");
        return PLACES.stream()
                .filter(p -> {
                    String text = (p.name() + " " + p.address() + " " + p.id() + " "
                            + (p.airportIata() == null ? "" : p.airportIata() + " " + p.terminal()))
                            .toLowerCase(Locale.ROOT);
                    for (String w : words) {
                        if (!text.contains(w)) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted(Comparator.comparing((Place p) -> !p.name().toLowerCase(Locale.ROOT).startsWith(words[0]))
                        .thenComparing(p -> !p.isAirport())
                        .thenComparing(Place::name))
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<Place> find(String placeId) {
        return PLACES.stream().filter(p -> p.id().equals(placeId)).findFirst();
    }

    @Override
    public Route route(Place from, Place to) {
        double km = haversineKm(from.latitude(), from.longitude(), to.latitude(), to.longitude()) * ROAD_FACTOR;
        // Slower in town, faster on motorways: 25 km/h up to 10 km, then 65 km/h.
        double hours = Math.min(km, 10) / 25.0 + Math.max(km - 10, 0) / 65.0;
        return new Route((int) Math.round(km * 1000), (int) Math.round(hours * 3600));
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
