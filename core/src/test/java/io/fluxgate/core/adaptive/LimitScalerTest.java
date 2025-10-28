package io.fluxgate.core.adaptive;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LimitScalerTest {

    @Test
    void scaleReturnsScaledLimitUsingAdaptiveState() {
        LimitScaler scaler = new LimitScaler();
        EwmaTrafficEstimator.AdaptiveState state = new EwmaTrafficEstimator.AdaptiveState(50d, 100d, 0L);

        double limit = scaler.scale(200d, state);

        assertThat(limit).isEqualTo(100d);
    }

    @Test
    void scaleFallsBackWhenShareNonPositive() {
        LimitScaler scaler = new LimitScaler();

        double limit = scaler.scale(100d, -1d);

        assertThat(limit).isEqualTo(100d);
    }

    @Test
    void scaleReturnsZeroWhenGlobalLimitZero() {
        LimitScaler scaler = new LimitScaler();
        EwmaTrafficEstimator.AdaptiveState state = new EwmaTrafficEstimator.AdaptiveState(1d, 1d, 0L);

        double limit = scaler.scale(0d, state);

        assertThat(limit).isZero();
        assertThat(scaler.scale(0d, 0d)).isZero();
    }
}
