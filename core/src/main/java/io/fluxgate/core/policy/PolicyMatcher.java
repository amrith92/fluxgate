package io.fluxgate.core.policy;

/**
 * Functional contract for compiled policy matchers. Implementations evaluate request
 * attributes against efficient data structures such as tries to ensure microsecond-level
 * latency under load.
 */
@FunctionalInterface
public interface PolicyMatcher {

    PolicyMatchResult evaluate(PolicyContext context);

    default boolean matches(PolicyContext context) {
        return evaluate(context).matched();
    }

    static PolicyMatcher always() {
        return ctx -> PolicyMatchResult.matched(KeyContext.empty());
    }
}
