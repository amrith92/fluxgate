package io.fluxgate.examples;

import io.fluxgate.adapters.resilience4j.Resilience4jAdapter;
import io.fluxgate.api.FluxGate;
import io.fluxgate.core.FluxGateLimiter;
import io.fluxgate.core.policy.CompiledPolicySet;
import io.fluxgate.core.policy.KeyBuilder;
import io.fluxgate.core.policy.LimitPolicy;
import io.fluxgate.core.policy.PolicyCompiler;
import io.fluxgate.core.policy.PolicyContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Demonstrates how to prepare request metadata so FluxGate's Resilience4j adapter can be
 * used inside a decorator or filter chain.
 */
public final class Resilience4jAdapterExample {

    private Resilience4jAdapterExample() {
    }

    public static void main(String[] args) {
        Path policyPath = Path.of("config", "limits.yaml");
        if (!Files.exists(policyPath)) {
            policyPath = Path.of("examples", "config", "limits.yaml");
        }

        CompiledPolicySet policies = PolicyCompiler.fromYaml(policyPath);
        FluxGate fluxGate = FluxGate.builder()
                .withPolicySet(policies)
                .withSecret("resilience4j-demo")
                .build();
        FluxGateLimiter limiter = fluxGate.limiter();

        // In a real application this would likely be a ThreadLocal scoped to the request.
        Map<Long, LimitPolicy> policyCache = new ConcurrentHashMap<>();
        Resilience4jAdapter adapter = new Resilience4jAdapter(limiter, key -> {
            LimitPolicy policy = policyCache.remove(key);
            if (policy == null) {
                throw new IllegalStateException("Missing policy for key " + key);
            }
            return policy;
        });

        Supplier<String> upstream = () -> "payload";
        Supplier<String> guarded = () -> {
            RequestMetadata metadata = RequestMetadata.of("203.0.113.24", "/checkout", Map.of());
            LimitPolicy policy = matchPolicy(policies, metadata);
            if (policy == null) {
                return upstream.get();
            }
            long keyHash = buildKeyHash(metadata, "resilience4j-demo");
            policyCache.put(keyHash, policy);
            if (!adapter.tryAcquire(keyHash)) {
                Duration retryAfter = adapter.retryAfter(keyHash);
                throw new IllegalStateException("Rate limited, retry after " + retryAfter);
            }
            return upstream.get();
        };

        for (int i = 0; i < 3; i++) {
            try {
                System.out.println("Call " + (i + 1) + ": " + guarded.get());
            } catch (IllegalStateException rateLimited) {
                System.out.println(rateLimited.getMessage());
            }
        }
    }

    private static LimitPolicy matchPolicy(CompiledPolicySet policies, RequestMetadata metadata) {
        PolicyContext context = new PolicyContext(metadata.ip(), metadata.route(), metadata.attributes());
        return policies.firstMatch(context).orElse(null);
    }

    private static long buildKeyHash(RequestMetadata metadata, String secret) {
        return KeyBuilder.of()
                .ip(metadata.ip())
                .route(metadata.route())
                .attributes(metadata.attributes())
                .buildHash(secret);
    }

    private record RequestMetadata(String ip, String route, Map<String, String> attributes) {
        static RequestMetadata of(String ip, String route, Map<String, String> attributes) {
            return new RequestMetadata(ip, route, attributes);
        }
    }
}
