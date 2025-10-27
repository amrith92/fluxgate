package io.fluxgate.core.policy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Immutable container for compiled policies. Provides evaluation helpers that walk the
 * matcher graph and produce per-policy decisions without additional allocation.
 */
public record CompiledPolicySet(List<LimitPolicy> policies) {

    public CompiledPolicySet {
        policies = List.copyOf(policies);
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
        return new CompiledPolicySet(List.copyOf(policies));
    }

    public List<PolicyBinding> bindings(PolicyContext context, String secret) {
        List<PolicyBinding> bindings = new ArrayList<>();
        for (LimitPolicy policy : policies) {
            PolicyMatchResult result = policy.matcher().evaluate(context);
            if (!result.matched()) {
                continue;
            }
            List<Long> keys = PolicyKeyBuilder.buildKeys(policy.id(), result, secret);
            if (!keys.isEmpty()) {
                bindings.add(new PolicyBinding(policy, keys));
            }
        }
        return bindings;
    }

    public record PolicyBinding(LimitPolicy policy, List<Long> keys) {
        public PolicyBinding {
            keys = List.copyOf(keys);
        }
    }
}
