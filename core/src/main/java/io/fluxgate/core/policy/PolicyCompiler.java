package io.fluxgate.core.policy;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public static CompiledPolicySet of(Collection<LimitPolicy> policies) {
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

    private static PolicyMatcher parseMatcher(Object node) {
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

    private static PolicyMatcher aggregate(List<PolicyMatcher> matchers) {
        if (matchers.isEmpty()) {
            return PolicyMatcher.always();
        }
        if (matchers.size() == 1) {
            return matchers.get(0);
        }
        return context -> {
            for (PolicyMatcher matcher : matchers) {
                if (!matcher.matches(context)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static PolicyMatcher allMatcher(Object node) {
        List<PolicyMatcher> matchers = new ArrayList<>();
        if (node instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                matchers.add(parseMatcher(element));
            }
        } else {
            matchers.add(parseMatcher(node));
        }
        return context -> {
            for (PolicyMatcher matcher : matchers) {
                if (!matcher.matches(context)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static PolicyMatcher anyMatcher(Object node) {
        List<PolicyMatcher> matchers = new ArrayList<>();
        if (node instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                matchers.add(parseMatcher(element));
            }
        } else {
            matchers.add(parseMatcher(node));
        }
        return context -> {
            for (PolicyMatcher matcher : matchers) {
                if (matcher.matches(context)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static PolicyMatcher notMatcher(Object node) {
        PolicyMatcher matcher = parseMatcher(node);
        return context -> !matcher.matches(context);
    }

    private static PolicyMatcher ipMatcher(Object node) {
        PatriciaTrie trie = new PatriciaTrie();
        if (node instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                trie.insert(element.toString());
            }
        } else {
            trie.insert(node.toString());
        }
        trie.freeze();
        return context -> trie.matches(context.ip());
    }

    private static PolicyMatcher routeMatcher(Object node) {
        RouteTrie trie = new RouteTrie();
        if (node instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                trie.insert(element.toString());
            }
        } else {
            trie.insert(node.toString());
        }
        return context -> trie.matches(context.route());
    }

    private static PolicyMatcher attributesMatcher(Object node) {
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

    private static PolicyMatcher singleAttributeMatcher(Object node) {
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

    private static PolicyMatcher attributeMatcher(String name, Object config) {
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
                    return false;
                }
                if (!equalsValues.isEmpty() && !equalsValues.contains(attribute)) {
                    return false;
                }
                if (!anyValues.isEmpty() && !anyValues.contains(attribute)) {
                    return false;
                }
                if (!noneValues.isEmpty() && noneValues.contains(attribute)) {
                    return false;
                }
                return true;
            };
        }
        if (config instanceof Iterable<?> iterable) {
            List<String> values = collectStrings(iterable);
            return context -> values.contains(context.attribute(name));
        }
        return context -> {
            String attribute = context.attribute(name);
            return attribute != null && attribute.equals(config.toString());
        };
    }

    private static List<String> collectStrings(Object node) {
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
