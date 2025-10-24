package io.fluxgate.core;

import io.fluxgate.core.Adaptive.EwmaTrafficEstimator;
import io.fluxgate.core.Adaptive.LimitScaler;
import io.fluxgate.core.Observability.FluxGateMetrics;
import io.fluxgate.core.Observability.FluxGateStats;
import io.fluxgate.core.Policy.LimitPolicy;
import io.fluxgate.core.Policy.PolicyCompiler;
import io.fluxgate.core.TierA.GcraLimiter;
import io.fluxgate.core.TierA.HybridHotKeyCache;
import io.fluxgate.core.TierB.CountMinLogSketch;
import io.fluxgate.core.TierB.HeavyKeeper;
import io.fluxgate.core.TierB.SliceRotator;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

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
    }

    public RateLimitOutcome check(long keyHash, Function<Long, LimitPolicy> policySupplier, long nowNanos) {
        Objects.requireNonNull(policySupplier, "policySupplier");
        LimitPolicy policy = policySupplier.apply(keyHash);
        if (policy == null) {
            return RateLimitOutcome.allowed();
        }

        double scaledLimit = limitScaler.scale(policy.limitPerSecond(), estimator.instanceShare());
        GcraLimiter limiter = hotCache.getOrCompute(keyHash,
                () -> new GcraLimiter(Duration.ofSeconds(1).toNanos(), scaledLimit, policy.burstTokens()));

        GcraLimiter.Outcome outcome = limiter.tryAcquire(nowNanos);
        if (outcome.allowed()) {
            metrics.recordAllowed();
            stats.onAllowed();
            sketch.increment(keyHash, nowNanos);
            heavyKeeper.offer(keyHash);
            rotator.rotateIfNeeded(nowNanos);
            return RateLimitOutcome.allowed();
        }

        metrics.recordBlocked();
        stats.onBlocked();
        return RateLimitOutcome.blocked(outcome.retryAfterNanos());
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

    public record RateLimitOutcome(boolean allowed, long retryAfterNanos) {
        public static RateLimitOutcome allowed() {
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
        private Collection<LimitPolicy> policies = PolicyCompiler.defaults();

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
