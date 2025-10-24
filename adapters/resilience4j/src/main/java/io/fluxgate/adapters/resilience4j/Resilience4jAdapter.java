package io.fluxgate.adapters.resilience4j;

import io.fluxgate.core.FluxGateLimiter;
import io.fluxgate.core.FluxGateLimiter.RateLimitOutcome;
import io.fluxgate.core.Policy.LimitPolicy;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

/**
 * Adapter that exposes a {@link FluxGateLimiter} through a Resilience4j-style contract.
 * This module allows optional integrations to depend on FluxGate without forcing the
 * core module to take a dependency on adapter concerns.
 */
public final class Resilience4jAdapter {

    private final FluxGateLimiter limiter;
    private final Function<Long, LimitPolicy> policyLookup;

    public Resilience4jAdapter(FluxGateLimiter limiter, Function<Long, LimitPolicy> policyLookup) {
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        this.policyLookup = Objects.requireNonNull(policyLookup, "policyLookup");
    }

    public boolean tryAcquire(long keyHash) {
        RateLimitOutcome outcome = limiter.check(keyHash, policyLookup, System.nanoTime());
        return outcome.allowed();
    }

    public Duration retryAfter(long keyHash) {
        RateLimitOutcome outcome = limiter.check(keyHash, policyLookup, System.nanoTime());
        if (outcome.allowed()) {
            return Duration.ZERO;
        }
        return Duration.ofNanos(outcome.retryAfterNanos());
    }
}
