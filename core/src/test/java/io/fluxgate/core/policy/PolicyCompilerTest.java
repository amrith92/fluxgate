package io.fluxgate.core.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyCompilerTest {

    @Test
    void fromYamlParsesPolicyDefinitions() {
        // Arrange
        String yaml = "policies:\n  - id: a\n    limitPerSecond: 10\n    burst: 5\n    windowSeconds: 30\n";

        // Act
        List<LimitPolicy> policies = PolicyCompiler.fromYaml(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        // Assert
        assertThat(policies).hasSize(1);
        assertThat(policies.get(0).id()).isEqualTo("a");
        assertThat(policies.get(0).limitPerSecond()).isEqualTo(10d);
        assertThat(policies.get(0).burstTokens()).isEqualTo(5d);
        assertThat(policies.get(0).windowSeconds()).isEqualTo(30L);
    }

    @Test
    void defaultsReturnsSinglePolicy() {
        // Arrange
        // Act
        List<LimitPolicy> policies = PolicyCompiler.defaults();

        // Assert
        assertThat(policies).hasSize(1);
        assertThat(policies.get(0).id()).isEqualTo("default");
    }
}
