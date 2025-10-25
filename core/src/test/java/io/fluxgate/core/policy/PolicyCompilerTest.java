package io.fluxgate.core.policy;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyCompilerTest {

    @Test
    void fromYamlParsesPolicyDefinitions() {
        // Arrange
        String yaml = "policies:\n" +
                "  - id: ip\n" +
                "    limitPerSecond: 10\n" +
                "    burst: 5\n" +
                "    windowSeconds: 30\n" +
                "    match:\n" +
                "      ip:\n" +
                "        - 10.0.0.0/8\n" +
                "        - 192.168.1.10\n" +
                "  - id: tier\n" +
                "    limitPerSecond: 50\n" +
                "    burst: 75\n" +
                "    windowSeconds: 60\n" +
                "    match:\n" +
                "      all:\n" +
                "        - route:\n" +
                "            - /accounts/**\n" +
                "            - /billing/*\n" +
                "        - attributes:\n" +
                "            tier:\n" +
                "              anyOf:\n" +
                "                - gold\n" +
                "                - platinum\n";

        // Act
        CompiledPolicySet policies = PolicyCompiler
                .fromYaml(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        // Assert
        assertThat(policies.policies()).hasSize(2);
        LimitPolicy ipPolicy = policies.policies().get(0);
        assertThat(ipPolicy.id()).isEqualTo("ip");
        assertThat(ipPolicy.matcher().matches(new PolicyContext("10.1.1.1", "/", Map.of()))).isTrue();
        LimitPolicy tierPolicy = policies.policies().get(1);
        PolicyContext billing = new PolicyContext("203.0.113.8", "/billing/invoice", Map.of("tier", "platinum"));
        assertThat(tierPolicy.matcher().matches(billing)).isTrue();
        PolicyContext denied = new PolicyContext("203.0.113.8", "/billing/invoice", Map.of("tier", "basic"));
        assertThat(tierPolicy.matcher().matches(denied)).isFalse();

        List<PolicyDecision> decisions = policies.evaluate(billing);
        assertThat(decisions).containsExactly(
                new PolicyDecision("ip", false),
                new PolicyDecision("tier", true));
    }

    @Test
    void defaultsReturnsSinglePolicy() {
        // Arrange
        // Act
        CompiledPolicySet policies = PolicyCompiler.defaults();

        // Assert
        assertThat(policies.policies()).hasSize(1);
        assertThat(policies.policies().get(0).id()).isEqualTo("default");
    }
}
