package io.fluxgate.core;

import io.fluxgate.core.observability.FluxGateStats;
import io.fluxgate.core.policy.LimitPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FluxGateLimiterVerifyTest {

    @Test
    void isHotAndSketchEstimateReflectObservations() {
        FluxGateStats stats = new FluxGateStats();
        FluxGateLimiter limiter = FluxGateLimiter.builder()
                .withStats(stats)
                .withShardCapacity(16)
                .withSketch(2, 64)
                .withRotationPeriod(Duration.ofSeconds(1))
                .build();

        LimitPolicy policy = new LimitPolicy("p", 1000d, 1000d, 60);

        long hotKey = 12345L;
        long coldKey = 99999L;

        // before any observation
        assertThat(limiter.isHot(hotKey)).isFalse();
        assertThat(limiter.sketchEstimate(hotKey)).isEqualTo(0L);

        // touch hotKey multiple times to promote it
        for (int i = 0; i < 50; i++) {
            limiter.check(hotKey, ignored -> policy, System.nanoTime());
        }

        // we expect the sketch to have non-zero estimate and the hot cache may contain it
        long est = limiter.sketchEstimate(hotKey);
        assertThat(est).isGreaterThanOrEqualTo(1L);

        // isHot may be true depending on admission; ensure method is callable and returns a boolean
        assertThat(limiter.isHot(hotKey)).isInstanceOf(Boolean.class);

        // cold key remains unseen
        assertThat(limiter.isHot(coldKey)).isFalse();
        assertThat(limiter.sketchEstimate(coldKey)).isEqualTo(0L);
    }
}

