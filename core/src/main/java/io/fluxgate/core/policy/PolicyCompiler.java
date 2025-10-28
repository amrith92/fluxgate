package io.fluxgate.core.policy;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class PolicyCompiler {

    private PolicyCompiler() {
        throw new IllegalStateException("Cannot instantitate " + PolicyCompiler.class);
    }

    public static CompiledPolicySet fromYaml(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return fromYaml(in);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read policies from " + path, e);
        }
    }

    public static CompiledPolicySet fromYaml(InputStream in) {
        Yaml yaml = new Yaml();
        Object root = yaml.load(in);
        if (!(root instanceof Map<?, ?> map)) {
            return new CompiledPolicySet(Collections.emptyList());
        }
        Object policiesNode = map.get("policies");
        if (!(policiesNode instanceof Iterable<?> iterable)) {
            return new CompiledPolicySet(Collections.emptyList());
        }
        List<LimitPolicy> policies = new ArrayList<>();
        for (Object element : iterable) {
            if (element instanceof Map<?, ?> policyMap) {
                policies.add(parsePolicy(policyMap));
            }
        }
        return new CompiledPolicySet(policies);
    }

    public static CompiledPolicySet defaults() {
        return new CompiledPolicySet(List.of(new LimitPolicy("default", 1d, 1d, 60)));
    }

    private static LimitPolicy parsePolicy(Map<?, ?> policyMap) {
        Object idNode = policyMap.get("id");
        String id = idNode != null ? idNode.toString() : "anonymous";
        double limit = toDouble(policyMap.get("limitPerSecond"), 100d);
        double burst = toDouble(policyMap.get("burst"), limit);
        long window = toLong(policyMap.get("windowSeconds"), 60L);
        PolicyMatcher matcher = parseMatcher(policyMap.get("match"));
        return new LimitPolicy(id, limit, burst, window, matcher);
    }

    static PolicyMatcher parseMatcher(Object node) {
        if (node == null) {
            return PolicyMatcher.always();
        }
        if (node instanceof Map<?, ?> map) {
            List<PolicyMatcher> matchers = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey().toString();
                Object value = entry.getValue();
                switch (key) {
                    case "all" -> matchers.add(allMatcher(value));
                    case "any" -> matchers.add(anyMatcher(value));
                    case "not" -> matchers.add(notMatcher(value));
                    case "ip" -> matchers.add(ipMatcher(value));
                    case "route" -> matchers.add(routeMatcher(value));
                    case "attributes" -> matchers.add(attributesMatcher(value));
                    case "attribute" -> matchers.add(singleAttributeMatcher(value));
                    case "headers" -> matchers.add(headersMatcher(value));
                    case "header" -> matchers.add(singleHeaderMatcher(value));
                    case "geo" -> matchers.add(geoMatcher(value));
                    case "routeGroups", "routeGroup" -> matchers.add(routeGroupsMatcher(value));
                    default -> throw new IllegalArgumentException("Unknown matcher key: " + key);
                }
            }
            return aggregate(matchers);
        }
        if (node instanceof Iterable<?> iterable) {
            List<PolicyMatcher> matchers = new ArrayList<>();
            for (Object element : iterable) {
                matchers.add(parseMatcher(element));
            }
            return aggregate(matchers);
        }
        throw new IllegalArgumentException("Unsupported matcher node: " + node);
    }

    static PolicyMatcher aggregate(List<PolicyMatcher> matchers) {
        if (matchers.isEmpty()) {
            return PolicyMatcher.always();
        }
        if (matchers.size() == 1) {
            return matchers.get(0);
        }
        return context -> {
            List<PolicyMatchResult> results = new ArrayList<>(matchers.size());
            for (PolicyMatcher matcher : matchers) {
                results.add(matcher.evaluate(context));
            }
            return PolicyMatchResult.aggregateAll(results);
        };
    }

    static PolicyMatcher allMatcher(Object node) {
        List<PolicyMatcher> matchers = new ArrayList<>();
        if (node instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                matchers.add(parseMatcher(element));
            }
        } else {
            matchers.add(parseMatcher(node));
        }
        return aggregate(matchers);
    }

    static PolicyMatcher anyMatcher(Object node) {
        List<PolicyMatcher> matchers = new ArrayList<>();
        if (node instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                matchers.add(parseMatcher(element));
            }
        } else {
            matchers.add(parseMatcher(node));
        }
        return context -> {
            List<PolicyMatchResult> results = new ArrayList<>(matchers.size());
            for (PolicyMatcher matcher : matchers) {
                results.add(matcher.evaluate(context));
            }
            return PolicyMatchResult.union(results);
        };
    }

    static PolicyMatcher notMatcher(Object node) {
        PolicyMatcher matcher = parseMatcher(node);
        return context -> matcher.evaluate(context).matched()
                ? PolicyMatchResult.notMatched()
                : PolicyMatchResult.matched(KeyContext.empty());
    }

    static PolicyMatcher ipMatcher(Object node) {
        PatriciaTrie trie = new PatriciaTrie();
        if (node instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                trie.insert(element.toString());
            }
        } else {
            trie.insert(node.toString());
        }
        trie.freeze();
        return context -> {
            String ip = context.ip();
            if (ip == null || !trie.matches(ip)) {
                return PolicyMatchResult.notMatched();
            }
            return PolicyMatchResult.matched(KeyContext.of(KeyDimension.ip(), ip));
        };
    }

    static PolicyMatcher routeMatcher(Object node) {
        RouteTrie trie = new RouteTrie();
        if (node instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                trie.insert(element.toString());
            }
        } else {
            trie.insert(node.toString());
        }
        return context -> {
            String route = context.route();
            if (route == null || !trie.matches(route)) {
                return PolicyMatchResult.notMatched();
            }
            return PolicyMatchResult.matched(KeyContext.of(KeyDimension.route(), route));
        };
    }

    static PolicyMatcher attributesMatcher(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("attributes matcher expects map");
        }
        List<PolicyMatcher> matchers = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String attribute = entry.getKey().toString();
            matchers.add(attributeMatcher(attribute, entry.getValue()));
        }
        return aggregate(matchers);
    }

    static PolicyMatcher singleAttributeMatcher(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("attribute matcher expects map");
        }
        Object nameNode = map.get("name");
        if (nameNode == null) {
            throw new IllegalArgumentException("attribute matcher requires name");
        }
        String attribute = nameNode.toString();
        Map<String, Object> config = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            if (!"name".equals(key)) {
                config.put(key, entry.getValue());
            }
        }
        if (config.isEmpty() && !map.containsKey("equals")) {
            throw new IllegalArgumentException("attribute matcher requires value");
        }
        Object valueConfig = config.isEmpty() ? map.get("equals") : config;
        return attributeMatcher(attribute, valueConfig);
    }

    static PolicyMatcher attributeMatcher(String name, Object config) {
        if (config instanceof Map<?, ?> map) {
            Object equals = map.get("equals");
            Object anyOf = map.get("anyOf");
            Object noneOf = map.get("noneOf");
            List<String> equalsValues = equals != null ? List.of(equals.toString()) : List.of();
            List<String> anyValues = collectStrings(anyOf);
            List<String> noneValues = collectStrings(noneOf);
            if (equalsValues.isEmpty() && anyValues.isEmpty() && noneValues.isEmpty()) {
                throw new IllegalArgumentException("attribute matcher requires equals/anyOf/noneOf");
            }
            return context -> {
                String attribute = context.attribute(name);
                if (attribute == null) {
                    return PolicyMatchResult.notMatched();
                }
                if (!equalsValues.isEmpty() && !equalsValues.contains(attribute)) {
                    return PolicyMatchResult.notMatched();
                }
                if (!anyValues.isEmpty() && !anyValues.contains(attribute)) {
                    return PolicyMatchResult.notMatched();
                }
                if (!noneValues.isEmpty() && noneValues.contains(attribute)) {
                    return PolicyMatchResult.notMatched();
                }
                return PolicyMatchResult.matched(KeyContext.of(KeyDimension.attribute(name), attribute));
            };
        }
        if (config instanceof Iterable<?> iterable) {
            List<String> values = collectStrings(iterable);
            return context -> {
                String attribute = context.attribute(name);
                if (attribute == null || !values.contains(attribute)) {
                    return PolicyMatchResult.notMatched();
                }
                return PolicyMatchResult.matched(KeyContext.of(KeyDimension.attribute(name), attribute));
            };
        }
        return context -> {
            String attribute = context.attribute(name);
            if (attribute == null || !attribute.equals(config.toString())) {
                return PolicyMatchResult.notMatched();
            }
            return PolicyMatchResult.matched(KeyContext.of(KeyDimension.attribute(name), attribute));
        };
    }

    static PolicyMatcher headersMatcher(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("headers matcher expects map");
        }
        List<PolicyMatcher> matchers = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String header = entry.getKey().toString();
            matchers.add(headerMatcher(header, entry.getValue()));
        }
        return aggregate(matchers);
    }

    static PolicyMatcher singleHeaderMatcher(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("header matcher expects map");
        }
        Object nameNode = map.get("name");
        if (nameNode == null) {
            throw new IllegalArgumentException("header matcher requires name");
        }
        String header = nameNode.toString();
        Map<String, Object> config = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            if (!"name".equals(key)) {
                config.put(key, entry.getValue());
            }
        }
        if (config.isEmpty() && !map.containsKey("equals")) {
            throw new IllegalArgumentException("header matcher requires value");
        }
        Object valueConfig = config.isEmpty() ? map.get("equals") : config;
        return headerMatcher(header, valueConfig);
    }

    static PolicyMatcher headerMatcher(String name, Object config) {
        if (config instanceof Map<?, ?> map) {
            Object equals = map.get("equals");
            Object anyOf = map.get("anyOf");
            Object noneOf = map.get("noneOf");
            List<String> equalsValues = equals != null ? List.of(equals.toString()) : List.of();
            List<String> anyValues = collectStrings(anyOf);
            List<String> noneValues = collectStrings(noneOf);
            if (equalsValues.isEmpty() && anyValues.isEmpty() && noneValues.isEmpty()) {
                throw new IllegalArgumentException("header matcher requires equals/anyOf/noneOf");
            }
            return context -> {
                String header = context.header(name);
                if (header == null) {
                    return PolicyMatchResult.notMatched();
                }
                if (!equalsValues.isEmpty() && !equalsValues.contains(header)) {
                    return PolicyMatchResult.notMatched();
                }
                if (!anyValues.isEmpty() && !anyValues.contains(header)) {
                    return PolicyMatchResult.notMatched();
                }
                if (!noneValues.isEmpty() && noneValues.contains(header)) {
                    return PolicyMatchResult.notMatched();
                }
                return PolicyMatchResult.matched(KeyContext.of(KeyDimension.header(name), header));
            };
        }
        if (config instanceof Iterable<?> iterable) {
            List<String> values = collectStrings(iterable);
            return context -> {
                String header = context.header(name);
                if (header == null || !values.contains(header)) {
                    return PolicyMatchResult.notMatched();
                }
                return PolicyMatchResult.matched(KeyContext.of(KeyDimension.header(name), header));
            };
        }
        return context -> {
            String header = context.header(name);
            if (header == null || !header.equals(config.toString())) {
                return PolicyMatchResult.notMatched();
            }
            return PolicyMatchResult.matched(KeyContext.of(KeyDimension.header(name), header));
        };
    }

    static PolicyMatcher geoMatcher(Object node) {
        if (node instanceof Map<?, ?> map) {
            Object equals = map.get("equals");
            Object anyOf = map.get("anyOf");
            Object noneOf = map.get("noneOf");
            List<String> equalsValues = equals != null ? List.of(equals.toString()) : List.of();
            List<String> anyValues = collectStrings(anyOf);
            List<String> noneValues = collectStrings(noneOf);
            if (equalsValues.isEmpty() && anyValues.isEmpty() && noneValues.isEmpty()) {
                throw new IllegalArgumentException("geo matcher requires equals/anyOf/noneOf");
            }
            return context -> {
                String geo = context.geo();
                if (geo == null) {
                    return PolicyMatchResult.notMatched();
                }
                if (!equalsValues.isEmpty() && !equalsValues.contains(geo)) {
                    return PolicyMatchResult.notMatched();
                }
                if (!anyValues.isEmpty() && !anyValues.contains(geo)) {
                    return PolicyMatchResult.notMatched();
                }
                if (!noneValues.isEmpty() && noneValues.contains(geo)) {
                    return PolicyMatchResult.notMatched();
                }
                return PolicyMatchResult.matched(KeyContext.of(KeyDimension.geo(), geo));
            };
        }
        if (node instanceof Iterable<?> iterable) {
            List<String> values = collectStrings(iterable);
            return context -> {
                String geo = context.geo();
                if (geo == null || !values.contains(geo)) {
                    return PolicyMatchResult.notMatched();
                }
                return PolicyMatchResult.matched(KeyContext.of(KeyDimension.geo(), geo));
            };
        }
        return context -> {
            String geo = context.geo();
            if (geo == null || !geo.equals(node.toString())) {
                return PolicyMatchResult.notMatched();
            }
            return PolicyMatchResult.matched(KeyContext.of(KeyDimension.geo(), geo));
        };
    }

    static PolicyMatcher routeGroupsMatcher(Object node) {
        if (node instanceof Map<?, ?> map) {
            String equals = map.get("equals") != null ? map.get("equals").toString() : null;
            List<String> anyValues = collectStrings(map.get("anyOf"));
            List<String> noneValues = collectStrings(map.get("noneOf"));
            if (equals == null && anyValues.isEmpty() && noneValues.isEmpty()) {
                throw new IllegalArgumentException("routeGroups matcher requires equals/anyOf/noneOf");
            }
            return context -> evaluateRouteGroups(context, equals, anyValues, noneValues);
        }
        if (node instanceof Iterable<?> iterable) {
            List<String> anyValues = collectStrings(iterable);
            return context -> evaluateRouteGroups(context, null, anyValues, List.of());
        }
        if (node != null) {
            return context -> evaluateRouteGroups(context, node.toString(), List.of(), List.of());
        }
        throw new IllegalArgumentException("routeGroups matcher requires configuration");
    }

    private static PolicyMatchResult evaluateRouteGroups(PolicyContext context,
                                                         String equalsValue,
                                                         List<String> anyValues,
                                                         List<String> noneValues) {
        List<String> groups = context.routeGroups();
        if (groups == null || groups.isEmpty()) {
            return PolicyMatchResult.notMatched();
        }
        LinkedHashSet<String> matched = new LinkedHashSet<>(groups);
        if (!noneValues.isEmpty()) {
            for (String value : noneValues) {
                if (matched.contains(value)) {
                    return PolicyMatchResult.notMatched();
                }
            }
        }
        if (equalsValue != null) {
            if (!matched.contains(equalsValue)) {
                return PolicyMatchResult.notMatched();
            }
            matched.clear();
            matched.add(equalsValue);
        }
        if (!anyValues.isEmpty()) {
            LinkedHashSet<String> intersection = new LinkedHashSet<>();
            for (String value : anyValues) {
                if (matched.contains(value)) {
                    intersection.add(value);
                }
            }
            if (intersection.isEmpty()) {
                return PolicyMatchResult.notMatched();
            }
            matched = intersection;
        }
        if (matched.isEmpty()) {
            return PolicyMatchResult.notMatched();
        }
        List<KeyContext> contexts = matched.stream()
                .map(value -> KeyContext.of(KeyDimension.routeGroup(), value))
                .toList();
        return PolicyMatchResult.matched(contexts);
    }

    static List<String> collectStrings(Object node) {
        if (node == null) {
            return List.of();
        }
        if (node instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object element : iterable) {
                values.add(element.toString());
            }
            return values;
        }
        return List.of(node.toString());
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
