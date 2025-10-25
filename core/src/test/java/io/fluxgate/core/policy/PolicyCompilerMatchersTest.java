package io.fluxgate.core.policy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyCompilerMatchersTest {

    @Test
    void aggregateAllAnyNotMatchers() {
        PolicyMatcher m1 = ctx -> ctx.route().startsWith("/a");
        PolicyMatcher m2 = ctx -> ctx.ip().startsWith("10.");
        PolicyMatcher all = PolicyCompiler.aggregate(List.of(m1, m2));
        assertThat(all.matches(new PolicyContext("10.0.0.1", "/a/1", Map.of()))).isTrue();
        assertThat(all.matches(new PolicyContext("10.0.0.1", "/b/1", Map.of()))).isFalse();

        PolicyMatcher any = PolicyCompiler.anyMatcher(
                List.of(Map.of("route", List.of("/a/**")),
                        Map.of("ip", List.of("10.0.0.0/8"))));
        assertThat(any.matches(new PolicyContext("10.1.1.1", "/x", Map.of()))).isTrue();

        PolicyMatcher not = PolicyCompiler.notMatcher(Map.of("ip", List.of("192.168.0.1")));
        assertThat(not.matches(new PolicyContext("192.168.0.2", "/", Map.of()))).isTrue();
        assertThat(not.matches(new PolicyContext("192.168.0.1", "/", Map.of()))).isFalse();
    }

    @Test
    void ipRouteAndAttributesMatchers() {
        // ip matcher list
        PolicyMatcher ip = PolicyCompiler.ipMatcher(List.of("10.0.0.0/8", "192.168.1.1"));
        assertThat(ip.matches(new PolicyContext("10.5.6.7", "/", Map.of()))).isTrue();
        assertThat(ip.matches(new PolicyContext("8.8.8.8", "/", Map.of()))).isFalse();

        // route matcher
        PolicyMatcher route = PolicyCompiler.routeMatcher(List.of("/foo/**", "/bar/*"));
        assertThat(route.matches(new PolicyContext("1.2.3.4", "/foo/x/y", Map.of()))).isTrue();
        assertThat(route.matches(new PolicyContext("1.2.3.4", "/bar/z", Map.of()))).isTrue();
        assertThat(route.matches(new PolicyContext("1.2.3.4", "/bar/z/w", Map.of()))).isFalse();

        // attributes matcher
        PolicyMatcher attrs = PolicyCompiler.attributesMatcher(Map.of(
                "tier", Map.of("anyOf", List.of("gold", "platinum")),
                "env", "prod"
        ));
        assertThat(attrs.matches(new PolicyContext("x", "/", Map.of("tier", "gold", "env", "prod")))).isTrue();
        assertThat(attrs.matches(new PolicyContext("x", "/", Map.of("tier", "silver", "env", "prod")))).isFalse();

        // single attribute matcher variants (equals/list/map)
        PolicyMatcher singleEq = PolicyCompiler.singleAttributeMatcher(Map.of("name", "tier", "equals", "gold"));
        assertThat(singleEq.matches(new PolicyContext("x", "/", Map.of("tier", "gold")))).isTrue();

        PolicyMatcher singleAny = PolicyCompiler.singleAttributeMatcher(
                Map.of("name", "tier", "anyOf", List.of("gold", "silver")));
        assertThat(singleAny.matches(new PolicyContext("x", "/", Map.of("tier", "silver")))).isTrue();

        PolicyMatcher singleNone = PolicyCompiler.singleAttributeMatcher(
                Map.of("name", "tier", "noneOf", List.of("gold")));
        assertThat(singleNone.matches(new PolicyContext("x", "/", Map.of("tier", "silver")))).isTrue();
    }

    @Test
    void attributeMatcherEdgeCases() {
        // null node
        assertThat(PolicyCompiler.parseMatcher(null).matches(new PolicyContext("x", "/", Map.of()))).isTrue();

        // invalid nodes
        assertThrows(IllegalArgumentException.class, () -> PolicyCompiler.parseMatcher(123));
        assertThrows(IllegalArgumentException.class, () -> PolicyCompiler.attributesMatcher("not-map"));
        assertThrows(IllegalArgumentException.class, () -> PolicyCompiler.singleAttributeMatcher(Map.of("foo", "bar")));

        // attribute matcher requires value
        assertThrows(IllegalArgumentException.class, () -> PolicyCompiler.attributeMatcher("tier", Map.of()));
    }
}
