package io.fluxgate.core.policy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

public final class PolicyCompiler {

    private PolicyCompiler() {}

    public static List<LimitPolicy> fromYaml(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return fromYaml(in);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read policies from " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<LimitPolicy> fromYaml(InputStream in) {
        Yaml yaml = new Yaml();
        Object root = yaml.load(in);
        if (!(root instanceof Map<?, ?> map)) {
            return Collections.emptyList();
        }
        Object policiesNode = map.get("policies");
        if (!(policiesNode instanceof Iterable<?> iterable)) {
            return Collections.emptyList();
        }
        List<LimitPolicy> policies = new ArrayList<>();
        for (Object element : iterable) {
            if (element instanceof Map<?, ?> policyMap) {
                Object idNode = policyMap.get("id");
                String id = idNode != null ? idNode.toString() : "anonymous";
                double limit = toDouble(policyMap.get("limitPerSecond"), 100d);
                double burst = toDouble(policyMap.get("burst"), limit);
                long window = toLong(policyMap.get("windowSeconds"), 60L);
                policies.add(new LimitPolicy(id, limit, burst, window));
            }
        }
        return policies;
    }

    public static List<LimitPolicy> defaults() {
        return List.of(new LimitPolicy("default", 1000d, 1000d, 60));
    }

    private static double toDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            return Double.parseDouble(value.toString());
        }
        return defaultValue;
    }

    private static long toLong(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            return Long.parseLong(value.toString());
        }
        return defaultValue;
    }
}
