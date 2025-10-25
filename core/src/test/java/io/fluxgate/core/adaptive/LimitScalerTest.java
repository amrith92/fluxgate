package io.fluxgate.core.adaptive;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LimitScalerTest {

    @Test
    void scaleReturnsScaledLimitForPositiveShare() {
        // Arrange
        LimitScaler scaler = new LimitScaler();

        // Act
        double limit = scaler.scale(100d, 0.5d);

        // Assert
        assertThat(limit).isEqualTo(50d);
    }

    @Test
    void scaleReturnsGlobalLimitWhenShareNonPositive() {
        // Arrange
        LimitScaler scaler = new LimitScaler();

        // Act
        double limit = scaler.scale(100d, 0d);

        // Assert
        assertThat(limit).isEqualTo(100d);
    }
}
