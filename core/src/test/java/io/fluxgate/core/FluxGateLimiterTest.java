package io.fluxgate.core;

import io.fluxgate.core.adaptive.EwmaTrafficEstimator;
import io.fluxgate.core.adaptive.LimitScaler;
import io.fluxgate.core.observability.FluxGateMetrics;
import io.fluxgate.core.observability.FluxGateStats;
import io.fluxgate.core.policy.LimitPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FluxGateLimiterTest {

    @Test
    void checkAllowsRequestWhenTokensAvailable() {
        // Arrange
        TestMetrics metrics = new TestMetrics();
        FluxGateStats stats = new FluxGateStats();
        FluxGateLimiter limiter = FluxGateLimiter.builder()
                .withMetrics(metrics)
                .withStats(stats)
                .withEstimator(new EwmaTrafficEstimator())
                .withLimitScaler(new LimitScaler())
                .withShardCapacity(4)
                .withSketch(2, 16)
                .withRotationPeriod(Duration.ofMillis(5))
                .build();
        LimitPolicy policy = new LimitPolicy("ip", 5d, 5d, 60);

        // Act
        FluxGateLimiter.RateLimitOutcome outcome = limiter.check(42L, ignored -> policy, 0L);

        // Assert
        assertThat(outcome.allowed()).isTrue();
        assertThat(metrics.allowed.get()).isEqualTo(1);
        assertThat(metrics.blocked.get()).isZero();
        assertThat(stats.totalRequests()).isEqualTo(1);
        assertThat(stats.blockedRequests()).isZero();
        assertThat(metrics.lastAdaptiveState.get()).isNotNull();
        assertThat(stats.adaptiveDebugView()).containsKeys("localQps", "clusterQps", "share");
    }

    @Test
    void checkBlocksWhenBurstExhausted() {
        // Arrange
        TestMetrics metrics = new TestMetrics();
        FluxGateStats stats = new FluxGateStats();
        FluxGateLimiter limiter = FluxGateLimiter.builder()
                .withMetrics(metrics)
                .withStats(stats)
                .withEstimator(new EwmaTrafficEstimator())
                .withLimitScaler(new LimitScaler())
                .withShardCapacity(4)
                .withSketch(2, 16)
                .withRotationPeriod(Duration.ofMillis(5))
                .build();
        LimitPolicy policy = new LimitPolicy("ip", 2d, 2d, 60);

        // Act
        limiter.check(99L, ignored -> policy, 0L);
        limiter.check(99L, ignored -> policy, 0L);
        limiter.check(99L, ignored -> policy, 0L);
        limiter.check(99L, ignored -> policy, 0L);
        FluxGateLimiter.RateLimitOutcome blocked = limiter.check(99L, ignored -> policy, 0L);

        // Assert
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterNanos()).isPositive();
        assertThat(metrics.allowed.get()).isEqualTo(4);
        assertThat(metrics.blocked.get()).isEqualTo(1);
        assertThat(stats.totalRequests()).isEqualTo(5);
        assertThat(stats.blockedRequests()).isEqualTo(1);
    }

    @Test
    void coldKeysAreThrottledProbabilistically() {
        TestMetrics metrics = new TestMetrics();
        FluxGateStats stats = new FluxGateStats();
        FluxGateLimiter limiter = FluxGateLimiter.builder()
                .withMetrics(metrics)
                .withStats(stats)
                .withEstimator(new EwmaTrafficEstimator())
                .withLimitScaler(new LimitScaler())
                .withShardCapacity(4)
                .withSketch(2, 16)
                .withRotationPeriod(Duration.ofMillis(5))
                .build();
        LimitPolicy policy = new LimitPolicy("ip", 1d, 1d, 5);

        FluxGateLimiter.RateLimitOutcome first = limiter.check(7L, ignored -> policy, 0L);
        FluxGateLimiter.RateLimitOutcome second = limiter.check(7L, ignored -> policy, 0L);
        FluxGateLimiter.RateLimitOutcome third = limiter.check(7L, ignored -> policy, 0L);

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isTrue();
        assertThat(third.allowed()).isFalse();
        assertThat(limiter.isHot(7L)).isFalse();
        assertThat(metrics.allowed.get()).isEqualTo(2);
        assertThat(metrics.blocked.get()).isEqualTo(1);
    }

    @Test
    void retryAfterForSketchBlocksIsConservative() {
        FluxGateLimiter limiter = FluxGateLimiter.builder()
                .withRotationPeriod(Duration.ofMillis(5))
                .build();
        LimitPolicy policy = new LimitPolicy("client", 1d, 1d, 5);

        limiter.check(11L, ignored -> policy, 0L);
        limiter.check(11L, ignored -> policy, 0L);
        FluxGateLimiter.RateLimitOutcome blocked = limiter.check(11L, ignored -> policy, 0L);

        long expectedMinimum = Duration.ofSeconds(6).toNanos();
        assertThat(blocked.retryAfterNanos()).isGreaterThanOrEqualTo(expectedMinimum);
    }

    @Test
    void ingestClusterQpsPublishesAdaptiveState() {
        TestMetrics metrics = new TestMetrics();
        FluxGateStats stats = new FluxGateStats();
        FluxGateLimiter limiter = FluxGateLimiter.builder()
                .withMetrics(metrics)
                .withStats(stats)
                .withEstimator(new EwmaTrafficEstimator())
                .withLimitScaler(new LimitScaler())
                .withShardCapacity(4)
                .withSketch(2, 16)
                .withRotationPeriod(Duration.ofMillis(5))
                .build();

        limiter.ingestClusterQps(250d, Duration.ofSeconds(1).toNanos());

        assertThat(metrics.lastAdaptiveState.get()).isNotNull();
        assertThat(stats.adaptiveState()).isNotNull();
        assertThat(stats.adaptiveState().clusterQps()).isGreaterThanOrEqualTo(1d);
    }

    @Test
    void registerPolicyStoresPolicyForIntrospection() {
        // Arrange
        FluxGateLimiter limiter = FluxGateLimiter.builder().build();
        LimitPolicy policy = new LimitPolicy("route", 10d, 15d, 120);

        // Act
        limiter.registerPolicy(policy);

        // Assert
        assertThat(limiter.policies()).contains(policy);
    }

    @Test
    void checkReturnsAllowedWhenPolicySupplierReturnsNull() {
        // Arrange
        FluxGateLimiter limiter = FluxGateLimiter.builder().build();

        // Act
        FluxGateLimiter.RateLimitOutcome outcome = limiter.check(1L, ignored -> null, 0L);

        // Assert
        assertThat(outcome.allowed()).isTrue();
    }

    private static final class TestMetrics implements FluxGateMetrics {

        private final AtomicInteger allowed = new AtomicInteger();
        private final AtomicInteger blocked = new AtomicInteger();
        private final AtomicReference<EwmaTrafficEstimator.AdaptiveState> lastAdaptiveState = new AtomicReference<>();

        @Override
        public void recordAllowed() {
            allowed.incrementAndGet();
        }

        @Override
        public void recordBlocked() {
            blocked.incrementAndGet();
        }

        @Override
        public void recordAdaptiveState(EwmaTrafficEstimator.AdaptiveState state) {
            lastAdaptiveState.set(state);
        }
    }
}
