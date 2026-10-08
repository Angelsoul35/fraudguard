package com.angelo.fraudguard.geo;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A tiny, self-contained geography table used by the location-based rules.
 *
 * <p>It maps a handful of ISO 3166-1 alpha-2 country codes to a representative
 * centroid (latitude/longitude in degrees) and exposes a great-circle distance
 * between two countries. This is deliberately a small lookup table rather than a
 * dependency on an external geo-IP service: the service must score offline and
 * deterministically, and a country centroid is accurate enough for an
 * "impossible travel" speed heuristic (we only need to know that two countries
 * are thousands of kilometres apart, not the exact city).
 *
 * <p>A production system would resolve the true merchant/terminal location and
 * use a proper geo database; see {@code docs/adr/0004}.
 */
public final class CountryGeo {

    private CountryGeo() {
    }

    /** Country centroid, degrees. */
    public record LatLon(double lat, double lon) {
    }

    private static final Map<String, LatLon> CENTROIDS = Map.ofEntries(
            Map.entry("US", new LatLon(39.8, -98.6)),
            Map.entry("CA", new LatLon(56.1, -106.3)),
            Map.entry("MX", new LatLon(23.6, -102.5)),
            Map.entry("BR", new LatLon(-14.2, -51.9)),
            Map.entry("CO", new LatLon(4.6, -74.3)),
            Map.entry("GB", new LatLon(55.4, -3.4)),
            Map.entry("ES", new LatLon(40.5, -3.7)),
            Map.entry("FR", new LatLon(46.2, 2.2)),
            Map.entry("DE", new LatLon(51.2, 10.4)),
            Map.entry("IT", new LatLon(41.9, 12.6)),
            Map.entry("RU", new LatLon(61.5, 105.3)),
            Map.entry("CN", new LatLon(35.9, 104.2)),
            Map.entry("JP", new LatLon(36.2, 138.3)),
            Map.entry("IN", new LatLon(20.6, 79.0)),
            Map.entry("AU", new LatLon(-25.3, 133.8)),
            Map.entry("ZA", new LatLon(-30.6, 22.9)),
            Map.entry("NG", new LatLon(9.1, 8.7)),
            Map.entry("AE", new LatLon(23.4, 53.8)),
            Map.entry("SG", new LatLon(1.4, 103.8)),
            Map.entry("KP", new LatLon(40.3, 127.5)));

    /**
     * Countries treated as elevated risk for card-not-present fraud in this demo.
     * This is an illustrative list for the heuristic, not a statement about any
     * country; a real deployment would drive this from a maintained risk feed.
     */
    private static final Set<String> HIGH_RISK = Set.of("RU", "KP", "NG");

    /** The centroid for a country code, if known. */
    public static Optional<LatLon> centroid(String country) {
        if (country == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CENTROIDS.get(country.toUpperCase()));
    }

    /** Whether the country code is known to this table. */
    public static boolean isKnown(String country) {
        return country != null && CENTROIDS.containsKey(country.toUpperCase());
    }

    /** Whether the country is on the demo high-risk list. */
    public static boolean isHighRisk(String country) {
        return country != null && HIGH_RISK.contains(country.toUpperCase());
    }

    /**
     * Great-circle (haversine) distance in kilometres between two countries'
     * centroids. Returns an empty value when either country is unknown.
     */
    public static Optional<Double> distanceKm(String a, String b) {
        Optional<LatLon> pa = centroid(a);
        Optional<LatLon> pb = centroid(b);
        if (pa.isEmpty() || pb.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(haversineKm(pa.get(), pb.get()));
    }

    private static double haversineKm(LatLon a, LatLon b) {
        final double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * earthRadiusKm * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }
}
