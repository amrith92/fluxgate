package io.fluxgate.core.policy;

import java.util.Map;

/**
 * Immutable view over request attributes that policy matchers inspect. Policies operate
 * on normalized IP and route strings together with arbitrary attribute name/value pairs.
 */
public record PolicyContext(String ip, String route, Map<String, String> attributes) {

    public PolicyContext {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public String attribute(String name) {
        return attributes.get(name);
    }
}
