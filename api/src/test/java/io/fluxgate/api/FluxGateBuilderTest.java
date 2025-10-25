package io.fluxgate.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FluxGateBuilderTest {

    @Test
    void builderUsesDefaultsWhenNoPoliciesProvided() {
        FluxGate fg = FluxGate.builder()
                .withSecret("s")
                .build();

        // default policies should be present in the underlying limiter
        assertThat(fg.limiter().policies()).isNotEmpty();
        boolean hasDefault = fg.limiter().policies().stream().anyMatch(p -> "default".equals(p.id()));
        assertThat(hasDefault).isTrue();
    }

    @Test
    void withConfigPathLoadsPoliciesFromYamlFile() throws Exception {
        String yaml = "policies:\n  - id: from-file\n    limitPerSecond: 2\n    burst: 2\n    windowSeconds: 10\n";
        Path tmp = Files.createTempFile("policies", ".yml");
        Files.writeString(tmp, yaml);

        FluxGate fg = FluxGate.builder()
                .withConfig(tmp)
                .withSecret("s")
                .build();

        assertThat(fg.limiter().policies()).anyMatch(p -> "from-file".equals(p.id()));

        // cleanup
        Files.deleteIfExists(tmp);
    }

    @Test
    void withConfigInputStreamLoadsPoliciesFromStream() {
        String yaml = "policies:\n  - id: from-stream\n    limitPerSecond: 3\n    burst: 3\n    windowSeconds: 5\n";
        InputStream in = new ByteArrayInputStream(yaml.getBytes());

        FluxGate fg = FluxGate.builder()
                .withConfig(in)
                .withSecret("s")
                .build();

        assertThat(fg.limiter().policies()).anyMatch(p -> "from-stream".equals(p.id()));
    }

    @Test
    void withSecretRejectsNull() {
        FluxGate.Builder b = FluxGate.builder();
        assertThrows(NullPointerException.class, () -> b.withSecret(null));
    }

    @Test
    void builderWithEmptyPoliciesLeadsToAllowOnCheck() {
        FluxGate fg = FluxGate.builder()
                .withPolicies(List.of())
                .withSecret("s")
                .build();

        FluxGate.RequestContext ctx = new SimpleContext("1.2.3.4", "/x");
        RateLimitResult res = fg.check(ctx);
        assertThat(res.isAllowed()).isTrue();
    }

    @Test
    void builderMutatorsChainAndLimiterAccessible() {
        FluxGate fg = FluxGate.builder()
                .withShardCapacity(123)
                .withSketch(2, 32)
                .withRotationPeriod(Duration.ofMillis(500))
                .withSecret("s")
                .build();

        // ensure limiter is accessible and non-null
        assertThat(fg.limiter()).isNotNull();
    }

    private record SimpleContext(String ip, String route) implements FluxGate.RequestContext {
    }
}
