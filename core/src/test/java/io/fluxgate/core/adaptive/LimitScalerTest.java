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
}
