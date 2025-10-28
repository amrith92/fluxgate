package io.fluxgate.core.policy;

import java.util.ArrayList;
import java.util.List;

/**
 * Result returned by a compiled matcher. When {@link #matched()} is {@code true} the
 * accompanying {@link KeyContext} list captures every key dimension combination that must be
 * evaluated for the policy.
 */
public record PolicyMatchResult(boolean matched, List<KeyContext> keyContexts) {

    private static final PolicyMatchResult NOT_MATCHED = new PolicyMatchResult(false, List.of());

    public PolicyMatchResult {
        keyContexts = keyContexts == null ? List.of() : List.copyOf(keyContexts);
    }

    public static PolicyMatchResult matched(KeyContext context) {
        return new PolicyMatchResult(true, List.of(context));
    }

    public static PolicyMatchResult matched(List<KeyContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return new PolicyMatchResult(true, List.of(KeyContext.empty()));
        }
        return new PolicyMatchResult(true, List.copyOf(contexts));
    }

    public static PolicyMatchResult aggregateAll(List<PolicyMatchResult> results) {
        List<KeyContext> contexts = new ArrayList<>();
        contexts.add(KeyContext.empty());
        for (PolicyMatchResult result : results) {
            if (!result.matched()) {
                return notMatched();
            }
            contexts = combine(contexts, result.keyContexts());
            if (contexts.isEmpty()) {
                return notMatched();
            }
        }
        return new PolicyMatchResult(true, contexts);
    }

    public static PolicyMatchResult notMatched() {
        return NOT_MATCHED;
    }

    public static PolicyMatchResult union(List<PolicyMatchResult> results) {
        List<KeyContext> contexts = new ArrayList<>();
        boolean matched = false;
        for (PolicyMatchResult result : results) {
            if (result.matched()) {
                matched = true;
                contexts.addAll(result.keyContexts());
            }
        }
        if (!matched) {
            return notMatched();
        }
        if (contexts.isEmpty()) {
            return new PolicyMatchResult(true, List.of(KeyContext.empty()));
        }
        return new PolicyMatchResult(true, List.copyOf(contexts));
    }

    private static List<KeyContext> combine(List<KeyContext> left, List<KeyContext> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return List.of();
        }
        List<KeyContext> combined = new ArrayList<>();
        for (KeyContext l : left) {
            for (KeyContext r : right) {
                l.merge(r).ifPresent(combined::add);
            }
        }
        return combined;
    }

}
