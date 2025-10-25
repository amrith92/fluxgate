package io.fluxgate.core.adaptive;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
