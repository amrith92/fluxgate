package io.fluxgate.core.policy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Immutable container for compiled policies. Provides evaluation helpers that walk the
 * matcher graph and produce per-policy decisions without additional allocation.
 */
public final class CompiledPolicySet {

    private final List<LimitPolicy> policies;

    public CompiledPolicySet(Collection<LimitPolicy> policies) {
        this.policies = List.copyOf(policies);
    }

    public List<LimitPolicy> policies() {
        return policies;
    }

    public List<PolicyDecision> evaluate(PolicyContext context) {
        List<PolicyDecision> results = new ArrayList<>(policies.size());
        for (LimitPolicy policy : policies) {
            boolean matched = policy.matcher().matches(context);
            results.add(new PolicyDecision(policy.id(), matched));
        }
        return results;
    }

    public Optional<LimitPolicy> firstMatch(PolicyContext context) {
        for (LimitPolicy policy : policies) {
            if (policy.matcher().matches(context)) {
                return Optional.of(policy);
            }
        }
        return Optional.empty();
    }

    public static CompiledPolicySet of(Collection<LimitPolicy> policies) {
        return new CompiledPolicySet(policies);
    }
}
