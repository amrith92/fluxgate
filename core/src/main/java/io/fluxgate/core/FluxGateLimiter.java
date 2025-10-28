package io.fluxgate.core;

import io.fluxgate.core.adaptive.EwmaTrafficEstimator;
import io.fluxgate.core.adaptive.LimitScaler;
import io.fluxgate.core.observability.FluxGateMetrics;
import io.fluxgate.core.observability.FluxGateStats;
import io.fluxgate.core.policy.CompiledPolicySet;
import io.fluxgate.core.policy.LimitPolicy;
import io.fluxgate.core.policy.PolicyCompiler;
import io.fluxgate.core.tierA.GcraLimiter;
import io.fluxgate.core.tierA.HybridHotKeyCache;
import io.fluxgate.core.tierB.CountMinLogSketch;
import io.fluxgate.core.tierB.HeavyKeeper;
import io.fluxgate.core.tierB.SliceRotator;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FluxGateLimiter orchestrates Tier A and Tier B flow control. It is intentionally
 * lightweight and exposes a minimal surface that the API module can wrap with richer
 * ergonomics.
 */
public final class FluxGateLimiter {

    private final HybridHotKeyCache<Long, GcraLimiter> hotCache;
    private final CountMinLogSketch sketch;
    private final HeavyKeeper heavyKeeper;
    private final FluxGateMetrics metrics;
    private final FluxGateStats stats;
    private final EwmaTrafficEstimator estimator;
    private final LimitScaler limitScaler;
    private final Map<String, LimitPolicy> policies;
    private final SliceRotator rotator;
    private final long rotationPeriodNanos;

    public FluxGateLimiter(Builder builder) {
        this.hotCache = new HybridHotKeyCache<>(builder.shardCapacity);
        this.sketch = new CountMinLogSketch(builder.sketchDepth, builder.sketchWidth, builder.sliceWindow);
        this.heavyKeeper = new HeavyKeeper(builder.heavyKeeperCapacity, builder.heavyKeeperDecay);
        this.metrics = builder.metrics;
        this.stats = builder.stats;
        this.estimator = builder.estimator;
        this.limitScaler = builder.limitScaler;
        this.policies = new ConcurrentHashMap<>();
        builder.policies.forEach(policy -> policies.put(policy.id(), policy));
        this.rotator = new SliceRotator(sketch, builder.rotationPeriod);
        this.rotationPeriodNanos = builder.rotationPeriod.toNanos();
    }

    public RateLimitOutcome check(long keyHash, LimitPolicy policy, long nowNanos) {
        if (policy == null) {
            return RateLimitOutcome.allow();
        }

        rotator.rotateIfNeeded(nowNanos);
        EwmaTrafficEstimator.AdaptiveState adaptiveState = estimator.observe(nowNanos);
        double scaledLimit = limitScaler.scale(policy.limitPerSecond(), adaptiveState);
        GcraLimiter existingLimiter = hotCache.getIfPresent(keyHash);
        if (existingLimiter != null) {
            return handleTierA(keyHash, nowNanos, adaptiveState, existingLimiter);
        }

        return handleTierB(keyHash, policy, nowNanos, adaptiveState, scaledLimit);
    }

    public void registerPolicy(LimitPolicy policy) {
        policies.put(policy.id(), policy);
    }

    public Collection<LimitPolicy> policies() {
        return policies.values();
    }

    public FluxGateMetrics metrics() {
        return metrics;
    }

    public FluxGateStats stats() {
        return stats;
    }

    public void ingestClusterQps(double clusterQps, long nowNanos) {
        EwmaTrafficEstimator.AdaptiveState state = estimator.ingestClusterEstimate(clusterQps, nowNanos);
        publishAdaptiveState(state);
    }

    public EwmaTrafficEstimator.AdaptiveState adaptiveState(long nowNanos) {
        return estimator.observe(nowNanos);
    }

    private void publishAdaptiveState(EwmaTrafficEstimator.AdaptiveState state) {
        metrics.recordAdaptiveState(state);
        stats.onAdaptiveUpdate(state);
    }

    private RateLimitOutcome handleTierA(long keyHash,
                                         long nowNanos,
                                         EwmaTrafficEstimator.AdaptiveState adaptiveState,
                                         GcraLimiter limiter) {
        GcraLimiter.Outcome outcome = limiter.tryAcquire(nowNanos);
        if (outcome.allowed()) {
            onAllowed(keyHash, nowNanos);
            return RateLimitOutcome.allow();
        }

        metrics.recordBlocked();
        stats.onBlocked();
        publishAdaptiveState(adaptiveState);
        return RateLimitOutcome.blocked(outcome.retryAfterNanos());
    }

    private RateLimitOutcome handleTierB(long keyHash,
                                         LimitPolicy policy,
                                         long nowNanos,
                                         EwmaTrafficEstimator.AdaptiveState adaptiveState,
                                         double scaledLimit) {
        long allowedBudget = computeAllowedBudget(scaledLimit, policy);
        long currentEstimate = sketch.estimate(keyHash);
        long projected = safeIncrement(currentEstimate);
        if (projected > allowedBudget) {
            metrics.recordBlocked();
            stats.onBlocked();
            publishAdaptiveState(adaptiveState);
            long retryAfter = computeSketchRetryAfterNanos(scaledLimit, policy);
            return RateLimitOutcome.blocked(retryAfter);
        }

        onAllowed(keyHash, nowNanos);
        long promotionThresholdCount = projected;
        if (shouldPromote(keyHash, scaledLimit, policy, allowedBudget, promotionThresholdCount)) {
            GcraLimiter limiter = hotCache.getOrCompute(keyHash,
                    () -> new GcraLimiter(Duration.ofSeconds(1).toNanos(), scaledLimit, policy.burstTokens()));
            limiter.tryAcquire(nowNanos);
        }
        return RateLimitOutcome.allow();
    }

    private void onAllowed(long keyHash, long nowNanos) {
        metrics.recordAllowed();
        stats.onAllowed();
        EwmaTrafficEstimator.AdaptiveState updatedState = estimator.recordLocalPermits(1L, nowNanos);
        publishAdaptiveState(updatedState);
        sketch.increment(keyHash, nowNanos);
        heavyKeeper.offer(keyHash);
    }

    private long computeAllowedBudget(double scaledLimit, LimitPolicy policy) {
        double rotationSeconds = rotationPeriodNanos / 1_000_000_000d;
        double sanitizedLimit = Math.max(0d, scaledLimit);
        double baseBudget = sanitizedLimit * rotationSeconds;
        if (rotationSeconds < 1d) {
            baseBudget = Math.max(baseBudget, sanitizedLimit);
        }

        double burstBudget;
        double burstTokens = Math.max(0d, policy.burstTokens());
        if (rotationSeconds >= 1d) {
            burstBudget = Math.max(0d, burstTokens - sanitizedLimit);
        } else {
            burstBudget = burstTokens;
        }

        long budget = (long) Math.ceil(baseBudget + burstBudget);
        if (budget <= 0L) {
            return 0L;
        }
        return Math.max(1L, budget);
    }

    private long computeSketchRetryAfterNanos(double scaledLimit, LimitPolicy policy) {
        double windowSeconds = Math.max(1d, policy.windowSeconds());
        double permitsPerSecond = Math.max(1d, scaledLimit);
        double burstSeconds = Math.max(0d, policy.burstTokens()) / permitsPerSecond;
        double totalSeconds = windowSeconds + burstSeconds;
        double nanos = totalSeconds * 1_000_000_000d;
        if (nanos >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, (long) Math.ceil(nanos));
    }

    private long safeIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1;
    }

    private boolean shouldPromote(long keyHash,
                                  double scaledLimit,
                                  LimitPolicy policy,
                                  long allowedBudget,
                                  long observedCount) {
        int heavyEstimate = heavyKeeper.estimate(keyHash);
        long burstFloor = (long) Math.ceil(Math.max(0d, policy.burstTokens()));
        long limitFloor = (long) Math.ceil(Math.max(0d, scaledLimit));
        long promotionThreshold = Math.max(10L, Math.max(burstFloor, limitFloor));
        if (heavyEstimate < promotionThreshold) {
            return false;
        }
        long requiredCount = Math.min(allowedBudget, promotionThreshold);
        return observedCount >= requiredCount;
    }

    /**
     * Returns whether a given key hash is currently present in the Tier-A hot cache.
     * This is a read-only helper used by benchmarks and diagnostics.
     */
    public boolean isHot(long keyHash) {
        return hotCache.isHot(keyHash);
    }

    /**
     * Returns the sketch's estimate for the provided key.
     * Exposed for benchmark verification and diagnostics.
     */
    public long sketchEstimate(long keyHash) {
        return sketch.estimate(keyHash);
    }

    public record RateLimitOutcome(boolean allowed, long retryAfterNanos) {
        public static RateLimitOutcome allow() {
            return new RateLimitOutcome(true, 0);
        }

        public static RateLimitOutcome blocked(long retryAfterNanos) {
            return new RateLimitOutcome(false, retryAfterNanos);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int shardCapacity = 65_536;
        private int sketchDepth = 4;
        private int sketchWidth = 1 << 16;
        private Duration rotationPeriod = Duration.ofSeconds(1);
        private Duration sliceWindow = Duration.ofSeconds(10);
        private int heavyKeeperCapacity = 1024;
        private double heavyKeeperDecay = 0.9d;
        private FluxGateMetrics metrics = FluxGateMetrics.noop();
        private FluxGateStats stats = new FluxGateStats();
        private EwmaTrafficEstimator estimator = new EwmaTrafficEstimator();
        private LimitScaler limitScaler = new LimitScaler();
        private Collection<LimitPolicy> policies = PolicyCompiler.defaults().policies();

        public Builder withShardCapacity(int shardCapacity) {
            this.shardCapacity = shardCapacity;
            return this;
        }

        public Builder withSketch(int depth, int width) {
            this.sketchDepth = depth;
            this.sketchWidth = width;
            return this;
        }

        public Builder withRotationPeriod(Duration rotationPeriod) {
            this.rotationPeriod = rotationPeriod;
            return this;
        }

        public Builder withSliceWindow(Duration sliceWindow) {
            this.sliceWindow = sliceWindow;
            return this;
        }

        public Builder withPolicies(Collection<LimitPolicy> policies) {
            this.policies = policies;
            return this;
        }

        public Builder withPolicySet(CompiledPolicySet policySet) {
            this.policies = policySet.policies();
            return this;
        }

        public Builder withMetrics(FluxGateMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder withStats(FluxGateStats stats) {
            this.stats = stats;
            return this;
        }

        public Builder withEstimator(EwmaTrafficEstimator estimator) {
            this.estimator = estimator;
            return this;
        }

        public Builder withLimitScaler(LimitScaler limitScaler) {
            this.limitScaler = limitScaler;
            return this;
        }

        public FluxGateLimiter build() {
            return new FluxGateLimiter(this);
        }
    }
}
