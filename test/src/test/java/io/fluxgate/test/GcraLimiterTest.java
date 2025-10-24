package io.fluxgate.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.fluxgate.core.TierA.GcraLimiter;
import org.junit.jupiter.api.Test;

public final class GcraLimiterTest {

    @Test
    void allowsBurstThenBlocks() {
        // Arrange
        GcraLimiter limiter = new GcraLimiter(1_000_000_000L, 10, 10);
        long now = 0L;

        // Act & Assert
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire(now).allowed()).isTrue();
        }

        // Assert
        assertThat(limiter.tryAcquire(now).allowed()).isFalse();
    }
}
