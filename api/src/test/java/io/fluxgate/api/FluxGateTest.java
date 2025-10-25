package io.fluxgate.api;

import io.fluxgate.core.policy.LimitPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FluxGateTest {

    @Test
    void checkAllowsWhenLimiterPermits() {
        // Arrange
        FluxGate limiter = FluxGate.builder()
                .withPolicies(List.of(new LimitPolicy("test", 5d, 5d, 60)))
                .withSecret("secret")
                .build();
        FluxGate.RequestContext context = new SimpleContext("127.0.0.1", "/health");

        // Act
        RateLimitResult result = limiter.check(context);

        // Assert
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.retryAfter().duration()).isZero();
    }

    @Test
    void checkBlocksWhenBurstExhausted() {
        // Arrange
        FluxGate limiter = FluxGate.builder()
                .withPolicies(List.of(new LimitPolicy("test", 1d, 1d, 60)))
                .withSecret("secret")
                .build();
        FluxGate.RequestContext context = new SimpleContext("127.0.0.1", "/health");

        // Act
        limiter.check(context);
        RateLimitResult blocked = limiter.check(context);

        // Assert
        assertThat(blocked.isAllowed()).isFalse();
        assertThat(blocked.retryAfter().duration().isZero()).isFalse();
    }

    private record SimpleContext(String ip, String route) implements FluxGate.RequestContext {
    }
}
