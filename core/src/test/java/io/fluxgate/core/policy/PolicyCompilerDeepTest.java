package io.fluxgate.core.policy;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyCompilerDeepTest {

    @Test
    void parseMatcherIterableAndUnknownKey() {
        // iterable of matchers should aggregate
        List<Object> nodes = List.of(Map.of("ip", "10.0.0.0/8"), Map.of("route", "/a/**"));
        PolicyMatcher m = PolicyCompiler.parseMatcher(nodes);
        assertThat(m.matches(new PolicyContext("10.1.1.1", "/a/x", Map.of()))).isTrue();

        // unknown key should throw
        Map<String, Object> bad = Map.of("unknown", "value");
        assertThrows(IllegalArgumentException.class, () -> PolicyCompiler.parseMatcher(bad));
    }

    @Test
    void attributeMatcherCombinations() {
        // equals path
        PolicyMatcher equals = PolicyCompiler.attributeMatcher("tier", Map.of("equals", "gold"));
        assertThat(equals.matches(new PolicyContext("x", "/", Map.of("tier", "gold")))).isTrue();
        assertThat(equals.matches(new PolicyContext("x", "/", Map.of("tier", "silver")))).isFalse();
        assertThat(equals.matches(new PolicyContext("x", "/", Map.of()))).isFalse();

        // anyOf path
        PolicyMatcher any = PolicyCompiler.attributeMatcher("tier", List.of("gold", "silver"));
        assertThat(any.matches(new PolicyContext("x", "/", Map.of("tier", "silver")))).isTrue();
        assertThat(any.matches(new PolicyContext("x", "/", Map.of("tier", "bronze")))).isFalse();

        // complex map with equals, anyOf and noneOf
        Map<String, Object> cfg = Map.of(
                "equals", "x",
                "anyOf", List.of("x", "y"),
                "noneOf", List.of("z")
        );
        PolicyMatcher complex = PolicyCompiler.attributeMatcher("tier", cfg);
        assertThat(complex.matches(new PolicyContext("x", "/", Map.of("tier", "x")))).isTrue();
        assertThat(complex.matches(
                new PolicyContext("x", "/", Map.of("tier", "y")))).isFalse(); // equals present so must match equals
        assertThat(complex.matches(new PolicyContext("x", "/", Map.of("tier", "z")))).isFalse();

        // empty config should throw (already covered elsewhere but assert here too)
        assertThrows(IllegalArgumentException.class, () -> PolicyCompiler.attributeMatcher("tier", Map.of()));
    }

    @Test
    void collectStringsAndNumberParsingFromYaml() {
        // collectStrings: single string
        var single = PolicyCompiler.collectStrings("one");
        assertThat(single).containsExactly("one");

        // collectStrings: iterable
        var list = PolicyCompiler.collectStrings(List.of("a", "b"));
        assertThat(list).containsExactly("a", "b");

        // fromYaml should parse numeric values provided as strings
        String yaml = """
                policies:
                  - id: numeric
                    limitPerSecond: "123.5"
                    burst: "10"
                    windowSeconds: "45"
                    match:
                      route: /x/**
                """;
        CompiledPolicySet set = PolicyCompiler
                .fromYaml(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        assertThat(set.policies()).hasSize(1);
        LimitPolicy p = set.policies().get(0);
        assertThat(p.id()).isEqualTo("numeric");
        // numeric fields were parsed
        assertThat(p.limitPerSecond()).isEqualTo(123.5d);
        assertThat(p.burstTokens()).isEqualTo(10d);
        assertThat(p.windowSeconds()).isEqualTo(45L);
    }
}
