package io.fluxgate.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.fluxgate.core.Adaptive.EwmaTrafficEstimator;
import io.fluxgate.core.Adaptive.LimitScaler;
import io.fluxgate.core.Observability.FluxGateMetrics;
import io.fluxgate.core.Observability.FluxGateStats;
import io.fluxgate.core.Policy.LimitPolicy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

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
        FluxGateLimiter.RateLimitOutcome blocked = limiter.check(99L, ignored -> policy, 0L);

        // Assert
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterNanos()).isPositive();
        assertThat(metrics.allowed.get()).isEqualTo(2);
        assertThat(metrics.blocked.get()).isEqualTo(1);
        assertThat(stats.totalRequests()).isEqualTo(3);
        assertThat(stats.blockedRequests()).isEqualTo(1);
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

        @Override
        public void recordAllowed() {
            allowed.incrementAndGet();
        }

        @Override
        public void recordBlocked() {
            blocked.incrementAndGet();
        }
    }
}
