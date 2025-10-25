package io.fluxgate.core.adaptive;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EwmaTrafficEstimatorTest {

    @Test
    void recordLocalPermitsUsesEwma() {
        EwmaTrafficEstimator estimator = new EwmaTrafficEstimator();

        EwmaTrafficEstimator.AdaptiveState state = estimator.recordLocalPermits(100,
                Duration.ofSeconds(1).toNanos());

        assertThat(state.localQps()).isCloseTo(20.8d, within(0.0001d));
        assertThat(state.clusterQps()).isCloseTo(20.8d, within(0.0001d));
        assertThat(state.share()).isEqualTo(1d);
    }

    @Test
    void ingestClusterEstimateAdjustsShare() {
        EwmaTrafficEstimator estimator = new EwmaTrafficEstimator();
        estimator.recordLocalPermits(100, Duration.ofSeconds(1).toNanos());

        EwmaTrafficEstimator.AdaptiveState state = estimator.ingestClusterEstimate(400,
                Duration.ofSeconds(2).toNanos());

        assertThat(state.clusterQps()).isCloseTo(96.64d, within(0.0001d));
        assertThat(state.share()).isCloseTo(20.8d / 96.64d, within(0.0001d));
    }

    @Test
    void observeFlushesPendingSamples() {
        EwmaTrafficEstimator estimator = new EwmaTrafficEstimator();
        estimator.recordLocalPermits(0, Duration.ofMillis(500).toNanos());

        EwmaTrafficEstimator.AdaptiveState state = estimator.observe(Duration.ofSeconds(1).toNanos());

        assertThat(state.localQps()).isGreaterThanOrEqualTo(1d);
        assertThat(state.debugView()).containsKeys("localQps", "clusterQps", "share");
    }
}
