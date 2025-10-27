package io.fluxgate.core.policy;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyKeyBuilderPlanTest {

    @Test
    void bindingsEnumerateAllMatchingDimensions() {
        String yaml = """
                policies:
                  - id: header-groups
                    limitPerSecond: 5
                    burst: 5
                    windowSeconds: 60
                    match:
                      all:
                        - header:
                            name: X-Tier
                            anyOf: [gold, platinum]
                        - routeGroups:
                            anyOf: [search, admin]
                  - id: geo-attribute
                    limitPerSecond: 10
                    burst: 10
                    windowSeconds: 60
                    match:
                      all:
                        - geo:
                            anyOf: [US, CA]
                        - attribute:
                            name: account
                            anyOf: [a1, a2]
                """;

        CompiledPolicySet policies = PolicyCompiler
                .fromYaml(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        PolicyContext context = new PolicyContext(
                "203.0.113.9",
                "/search/query",
                Map.of("account", "a1"),
                Map.of("X-Tier", "gold"),
                "US",
                List.of("search", "admin")
        );

        List<CompiledPolicySet.PolicyBinding> bindings = policies.bindings(context, "secret");
        assertThat(bindings).hasSize(2);

        Map<String, List<Long>> keysByPolicy = bindings.stream()
                .collect(Collectors.toMap(b -> b.policy().id(), CompiledPolicySet.PolicyBinding::keys));

        assertThat(keysByPolicy.get("header-groups")).containsExactlyInAnyOrder(
                KeyBuilder.of().policy("header-groups").header("X-Tier", "gold").routeGroup("search").buildHash("secret"),
                KeyBuilder.of().policy("header-groups").header("X-Tier", "gold").routeGroup("admin").buildHash("secret")
        );

        assertThat(keysByPolicy.get("geo-attribute")).containsExactly(
                KeyBuilder.of()
                        .policy("geo-attribute")
                        .geo("US")
                        .attribute("account", "a1")
                        .buildHash("secret")
        );
    }

    @Test
    void defaultPolicyProducesBinding() {
        CompiledPolicySet policies = PolicyCompiler.defaults();
        PolicyContext context = new PolicyContext(
                "198.51.100.7",
                "/unknown",
                Map.of(),
                Map.of(),
                "DE",
                List.of()
        );

        List<CompiledPolicySet.PolicyBinding> bindings = policies.bindings(context, "secret");
        assertThat(bindings).hasSize(1);
        long expected = KeyBuilder.of().policy("default").buildHash("secret");
        assertThat(bindings.get(0).keys()).containsExactly(expected);
    }
}
