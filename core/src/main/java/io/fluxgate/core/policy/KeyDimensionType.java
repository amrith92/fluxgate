package io.fluxgate.core.policy;

/**
 * Enumerates the supported dimensions that can contribute to a policy key.
 */
public enum KeyDimensionType {
    POLICY,
    IP,
    ROUTE,
    ATTRIBUTE,
    HEADER,
    GEO,
    ROUTE_GROUP
}
