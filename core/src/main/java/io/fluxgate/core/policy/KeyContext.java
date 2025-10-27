package io.fluxgate.core.policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the key dimensions contributed by a matcher branch. Contexts can be combined
 * via {@link #merge(KeyContext)} to build the cartesian product of all contributing
 * dimensions.
 */
public record KeyContext(Map<KeyDimension, String> dimensions) {

    public KeyContext {
        Map<KeyDimension, String> sanitized = new LinkedHashMap<>();
        if (dimensions != null) {
            dimensions.forEach((dimension, value) -> {
                Objects.requireNonNull(dimension, "dimension");
                Objects.requireNonNull(value, "value");
                sanitized.put(dimension, value);
            });
        }
        dimensions = Collections.unmodifiableMap(sanitized);
    }

    public static KeyContext empty() {
        return new KeyContext(Map.of());
    }

    public static KeyContext of(KeyDimension dimension, String value) {
        return new KeyContext(Map.of(dimension, value));
    }

    public Optional<KeyContext> merge(KeyContext other) {
        LinkedHashMap<KeyDimension, String> merged = new LinkedHashMap<>(dimensions);
        for (Map.Entry<KeyDimension, String> entry : other.dimensions.entrySet()) {
            String existing = merged.putIfAbsent(entry.getKey(), entry.getValue());
            if (existing != null && !existing.equals(entry.getValue())) {
                return Optional.empty();
            }
        }
        return Optional.of(new KeyContext(merged));
    }

    public List<Map.Entry<KeyDimension, String>> sortedEntries() {
        List<Map.Entry<KeyDimension, String>> entries = new ArrayList<>(dimensions.entrySet());
        entries.sort((a, b) -> {
            int cmp = Integer.compare(a.getKey().type().ordinal(), b.getKey().type().ordinal());
            if (cmp != 0) {
                return cmp;
            }
            String left = a.getKey().name() == null ? "" : a.getKey().name();
            String right = b.getKey().name() == null ? "" : b.getKey().name();
            return left.compareTo(right);
        });
        return entries;
    }
}
