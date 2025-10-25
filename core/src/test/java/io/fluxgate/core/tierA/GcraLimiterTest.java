package io.fluxgate.core.tierA;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GcraLimiterTest {

    @Test
    void tryAcquireAllowsWithinBurstThenBlocks() {
        // Arrange
        GcraLimiter limiter = new GcraLimiter(1_000_000_000L, 5, 5);
        long now = 0L;

        // Act
        boolean firstAllowed = limiter.tryAcquire(now).allowed();
        boolean secondAllowed = limiter.tryAcquire(now).allowed();
        boolean thirdAllowed = limiter.tryAcquire(now).allowed();
        boolean fourthAllowed = limiter.tryAcquire(now).allowed();
        boolean fifthAllowed = limiter.tryAcquire(now).allowed();
        GcraLimiter.Outcome blocked = limiter.tryAcquire(now);

        // Assert
        assertThat(firstAllowed).isTrue();
        assertThat(secondAllowed).isTrue();
        assertThat(thirdAllowed).isTrue();
        assertThat(fourthAllowed).isTrue();
        assertThat(fifthAllowed).isTrue();
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterNanos()).isPositive();
    }

    @Test
    void tryAcquireResetsAfterWaitPeriod() {
        // Arrange
        GcraLimiter limiter = new GcraLimiter(1_000_000_000L, 1, 1);
        long now = 0L;

        // Act
        limiter.tryAcquire(now);
        GcraLimiter.Outcome blocked = limiter.tryAcquire(now);
        GcraLimiter.Outcome afterWait = limiter.tryAcquire(now + blocked.retryAfterNanos());

        // Assert
        assertThat(blocked.allowed()).isFalse();
        assertThat(afterWait.allowed()).isTrue();
    }
}
