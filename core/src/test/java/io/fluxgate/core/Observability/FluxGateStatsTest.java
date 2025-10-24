package io.fluxgate.core.Observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FluxGateStatsTest {

    @Test
    void onAllowedIncrementsTotalOnly() {
        // Arrange
        FluxGateStats stats = new FluxGateStats();

        // Act
        stats.onAllowed();

        // Assert
        assertThat(stats.totalRequests()).isEqualTo(1);
        assertThat(stats.blockedRequests()).isZero();
    }

    @Test
    void onBlockedIncrementsTotalAndBlocked() {
        // Arrange
        FluxGateStats stats = new FluxGateStats();

        // Act
        stats.onBlocked();

        // Assert
        assertThat(stats.totalRequests()).isEqualTo(1);
        assertThat(stats.blockedRequests()).isEqualTo(1);
    }
}
