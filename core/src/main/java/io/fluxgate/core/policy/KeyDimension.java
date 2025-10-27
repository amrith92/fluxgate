package io.fluxgate.core.policy;

import java.util.Objects;

/**
 * Identifies a specific key dimension. Some dimensions such as attributes and headers also
 * carry a logical name.
 */
public record KeyDimension(KeyDimensionType type, String name) {

    public KeyDimension {
        Objects.requireNonNull(type, "type");
    }

    public static KeyDimension policy() {
        return new KeyDimension(KeyDimensionType.POLICY, null);
    }

    public static KeyDimension ip() {
        return new KeyDimension(KeyDimensionType.IP, null);
    }

    public static KeyDimension route() {
        return new KeyDimension(KeyDimensionType.ROUTE, null);
    }

    public static KeyDimension attribute(String name) {
        return new KeyDimension(KeyDimensionType.ATTRIBUTE, name);
    }

    public static KeyDimension header(String name) {
        return new KeyDimension(KeyDimensionType.HEADER, name);
    }

    public static KeyDimension geo() {
        return new KeyDimension(KeyDimensionType.GEO, null);
    }

    public static KeyDimension routeGroup() {
        return new KeyDimension(KeyDimensionType.ROUTE_GROUP, null);
    }
}
