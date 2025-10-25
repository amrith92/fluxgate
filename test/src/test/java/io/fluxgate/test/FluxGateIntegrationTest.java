package io.fluxgate.test;

import io.fluxgate.api.FluxGate;
import io.fluxgate.api.RateLimitResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class FluxGateIntegrationTest {

    @Test
    void simpleAllowAndBlock() {
        // Arrange
        FluxGate limiter = FluxGate.builder().build();
        FluxGate.RequestContext ctx = new FluxGate.RequestContext() {
            @Override
            public String ip() {
                return "127.0.0.1";
            }

            @Override
            public String route() {
                return "/";
            }
        };

        // Act
        RateLimitResult first = limiter.check(ctx);
        RateLimitResult second = limiter.check(ctx);

        // Assert
        assertThat(first.isAllowed()).isTrue();
        assertThat(second.isAllowed()).isFalse();
    }
}
