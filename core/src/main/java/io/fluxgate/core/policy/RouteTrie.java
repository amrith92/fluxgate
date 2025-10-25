package io.fluxgate.core.policy;

import java.util.HashMap;
import java.util.Map;

/**
 * Path matcher with segment level trie supporting '*' (single segment) and '**' (multi-segment)
 * wildcards. The trie is optimized for read-heavy workloads and does not allocate during
 * matching which keeps GC overhead minimal.
 */
final class RouteTrie {

    private final Node root = new Node();

    void insert(String pattern) {
        String normalized = normalize(pattern);
        String[] segments = normalized.isEmpty() ? new String[0] : normalized.split("/");
        Node node = root;
        for (String segment : segments) {
            if (segment.equals("**")) {
                node.multiWildcard = true;
                node = node.ensureTerminal();
                return;
            }
            if (segment.equals("*")) {
                node = node.ensureSingleWildcard();
                continue;
            }
            node = node.ensureChild(segment);
        }
        node.matchHere = true;
    }

    boolean matches(String route) {
        String normalized = normalize(route);
        String[] segments = normalized.isEmpty() ? new String[0] : normalized.split("/");
        return matches(root, segments, 0);
    }

    private boolean matches(Node node, String[] segments, int index) {
        if (index == segments.length) {
            return node.matchHere || node.multiWildcard;
        }
        if (node.multiWildcard && (node.matchHere || matches(node, segments, index + 1))) {
            return true;
        }
        String segment = segments[index];
        Node exact = node.children.get(segment);
        if (exact != null && matches(exact, segments, index + 1)) {
            return true;
        }
        if (node.singleWildcard != null && matches(node.singleWildcard, segments, index + 1)) {
            return true;
        }
        if (node.multiWildcard) {
            return matches(node, segments, index + 1);
        }
        return false;
    }

    private static String normalize(String route) {
        if (route == null || route.isEmpty() || route.equals("/")) {
            return "";
        }
        String normalized = route.startsWith("/") ? route.substring(1) : route;
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static final class Node {
        private final Map<String, Node> children = new HashMap<>();
        private Node singleWildcard;
        private boolean multiWildcard;
        private boolean matchHere;

        Node ensureChild(String segment) {
            return children.computeIfAbsent(segment, ignored -> new Node());
        }

        Node ensureSingleWildcard() {
            if (singleWildcard == null) {
                singleWildcard = new Node();
            }
            return singleWildcard;
        }

        Node ensureTerminal() {
            matchHere = true;
            return this;
        }
    }
}
