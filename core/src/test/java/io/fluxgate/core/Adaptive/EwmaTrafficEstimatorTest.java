package io.fluxgate.core.Adaptive;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EwmaTrafficEstimatorTest {

    @Test
    void recordUpdatesEstimateUsingEwma() {
        // Arrange
        EwmaTrafficEstimator estimator = new EwmaTrafficEstimator();

        // Act
        estimator.record(100);
        double share = estimator.instanceShare();

        // Assert
        assertThat(share).isEqualTo(20d);
    }

    @Test
    void recordNeverDropsBelowOne() {
        // Arrange
        EwmaTrafficEstimator estimator = new EwmaTrafficEstimator();

        // Act
        estimator.record(0);
        double share = estimator.instanceShare();

        // Assert
        assertThat(share).isEqualTo(1d);
    }
}
