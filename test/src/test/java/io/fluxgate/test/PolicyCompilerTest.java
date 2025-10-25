package io.fluxgate.test;

import io.fluxgate.core.policy.PolicyCompiler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

final class PolicyCompilerTest {

    @Test
    void parsesYaml() {
        // Arrange
        String yaml = "policies:\n  - id: ip-global\n    limitPerSecond: 10\n    burst: 5\n    windowSeconds: 60\n";

        // Act
        var policies = PolicyCompiler
                .fromYaml(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        // Assert
        assertThat(policies).hasSize(1);
        assertThat(policies.get(0).id()).isEqualTo("ip-global");
    }
}
