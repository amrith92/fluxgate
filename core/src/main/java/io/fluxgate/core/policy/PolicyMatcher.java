package io.fluxgate.core.policy;

/**
 * Functional contract for compiled policy matchers. Implementations evaluate request
 * attributes against efficient data structures such as tries to ensure microsecond-level
 * latency under load.
 */
@FunctionalInterface
public interface PolicyMatcher {

    boolean matches(PolicyContext context);

    static PolicyMatcher always() {
        return ctx -> true;
    }
}
