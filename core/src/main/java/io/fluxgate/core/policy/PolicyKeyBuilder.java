package io.fluxgate.core.policy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Produces deterministic key hashes for a policy based on the evaluated matcher result.
 */
public final class PolicyKeyBuilder {

    private PolicyKeyBuilder() {
        // utility
    }

    public static List<Long> buildKeys(String policyId, PolicyMatchResult result, String secret) {
        if (!result.matched()) {
            return List.of();
        }
        LinkedHashSet<Long> hashes = new LinkedHashSet<>();
        for (KeyContext context : result.keyContexts()) {
            KeyBuilder builder = KeyBuilder.of().policy(policyId);
            for (Map.Entry<KeyDimension, String> entry : context.sortedEntries()) {
                switch (entry.getKey().type()) {
                    case POLICY -> builder.policy(entry.getValue());
                    case IP -> builder.ip(entry.getValue());
                    case ROUTE -> builder.route(entry.getValue());
                    case ATTRIBUTE -> builder.attribute(entry.getKey().name(), entry.getValue());
                    case HEADER -> builder.header(entry.getKey().name(), entry.getValue());
                    case GEO -> builder.geo(entry.getValue());
                    case ROUTE_GROUP -> builder.routeGroup(entry.getValue());
                }
            }
            hashes.add(builder.buildHash(secret));
        }
        return List.copyOf(hashes);
    }
}
