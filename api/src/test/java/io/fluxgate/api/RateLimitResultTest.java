package io.fluxgate.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitResultTest {

    @Test
    void allowedFactoryProducesAllowedResult() {
        // Arrange
        // Act
        RateLimitResult result = RateLimitResult.allowed();

        // Assert
        assertThat(result.allowed()).isTrue();
        assertThat(result.retryAfter()).isEqualTo(RetryAfter.zero());
    }

    @Test
    void blockedFactoryWrapsRetryAfter() {
        // Arrange
        RetryAfter retryAfter = RetryAfter.ofSeconds(5);

        // Act
        RateLimitResult result = RateLimitResult.blocked(retryAfter);

        // Assert
        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfter()).isEqualTo(retryAfter);
        assertThat(result.retryAfter().seconds()).isEqualTo(5L);
    }

    @Test
    void retryAfterProvidesDurationHelpers() {
        // Arrange
        RetryAfter fromNanos = RetryAfter.ofNanos(1_000_000);

        // Act
        long seconds = fromNanos.seconds();

        // Assert
        assertThat(seconds).isZero();
        assertThat(RetryAfter.ofSeconds(2).duration()).isEqualTo(Duration.ofSeconds(2));
    }
}
