package io.fluxgate.core.policy;

/**
 * Outcome for a single policy evaluation. The matcher determines whether a policy applies
 * to the provided context and exposes the decision together with the policy identifier.
 */
public record PolicyDecision(String policyId, boolean matched) {
}
