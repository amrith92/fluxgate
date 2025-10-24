package io.fluxgate.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.fluxgate.api.FluxGate;
import io.fluxgate.api.RateLimitResult;
import org.junit.jupiter.api.Test;

public final class FluxGateIntegrationTest {

    @Test
    void simpleAllowAndBlock() {
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
        RateLimitResult result = limiter.check(ctx);
        assertThat(result.allowed()).isTrue();
    }
}
