package io.fluxgate.core.policy;

import java.util.List;
import java.util.Map;

/**
 * Immutable view over request attributes that policy matchers inspect. Policies operate
 * on normalized IP and route strings together with arbitrary attribute name/value pairs.
 */
public record PolicyContext(String ip,
                            String route,
                            Map<String, String> attributes,
                            Map<String, String> headers,
                            String geo,
                            List<String> routeGroups) {

    public PolicyContext {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        routeGroups = routeGroups == null ? List.of() : List.copyOf(routeGroups);
    }

    public PolicyContext(String ip, String route, Map<String, String> attributes) {
        this(ip, route, attributes, Map.of(), null, List.of());
    }

    public PolicyContext(String ip,
                         String route,
                         Map<String, String> attributes,
                         Map<String, String> headers) {
        this(ip, route, attributes, headers, null, List.of());
    }

    public PolicyContext(String ip,
                         String route,
                         Map<String, String> attributes,
                         Map<String, String> headers,
                         String geo) {
        this(ip, route, attributes, headers, geo, List.of());
    }

    public String attribute(String name) {
        return attributes.get(name);
    }

    public String header(String name) {
        return headers.get(name);
    }

    public List<String> routeGroups() {
        return routeGroups;
    }
}
