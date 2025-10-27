package io.fluxgate.api;

import io.fluxgate.core.FluxGateLimiter;
import io.fluxgate.core.policy.CompiledPolicySet;
import io.fluxgate.core.policy.LimitPolicy;
import io.fluxgate.core.policy.PolicyCompiler;
import io.fluxgate.core.policy.PolicyContext;
import io.fluxgate.core.policy.PolicyDecision;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FluxGate {

    private final FluxGateLimiter limiter;
    private final CompiledPolicySet policySet;
    private final String secret;

    private FluxGate(Builder builder) {
        this.secret = builder.secret;
        CompiledPolicySet compiled = builder.policySet;
        if (compiled == null) {
            if (builder.policyPath != null) {
                compiled = PolicyCompiler.fromYaml(Path.of(builder.policyPath));
            } else if (builder.policyStream != null) {
                compiled = PolicyCompiler.fromYaml(builder.policyStream);
            } else {
                compiled = PolicyCompiler.defaults();
            }
        }

        this.policySet = compiled;
        this.limiter = FluxGateLimiter.builder()
                .withPolicySet(compiled)
                .withShardCapacity(builder.shardCapacity)
                .withSketch(builder.sketchDepth, builder.sketchWidth)
                .withRotationPeriod(builder.rotationPeriod)
                .build();
    }

    public RateLimitResult check(RequestContext ctx) {
        Map<String, String> attributes = ctx.attributes();
        PolicyContext context = new PolicyContext(ctx.ip(), ctx.route(), attributes, ctx.headers(), ctx.geo(), ctx.routeGroups());
        List<CompiledPolicySet.PolicyBinding> bindings = policySet.bindings(context, secret);
        if (bindings.isEmpty()) {
            return RateLimitResult.allowed();
        }
        long now = System.nanoTime();
        boolean allowed = true;
        long retryAfter = 0L;
        for (CompiledPolicySet.PolicyBinding binding : bindings) {
            LimitPolicy policy = binding.policy();
            for (Long key : binding.keys()) {
                FluxGateLimiter.RateLimitOutcome outcome = limiter.check(key, policy, now);
                if (!outcome.allowed()) {
                    allowed = false;
                    retryAfter = Math.max(retryAfter, outcome.retryAfterNanos());
                }
            }
        }
        if (allowed) {
            return RateLimitResult.allowed();
        }
        return RateLimitResult.blocked(RetryAfter.ofNanos(retryAfter));
    }

    public List<PolicyDecision> evaluatePolicies(RequestContext ctx) {
        PolicyContext context = new PolicyContext(ctx.ip(), ctx.route(), ctx.attributes(), ctx.headers(), ctx.geo(), ctx.routeGroups());
        return policySet.evaluate(context);
    }

    public FluxGateLimiter limiter() {
        return limiter;
    }

    public static Builder builder() {
        return new Builder();
    }

    public interface RequestContext {
        String ip();

        String route();

        default Map<String, String> attributes() {
            return Map.of();
        }

        default Map<String, String> headers() {
            return Map.of();
        }

        default String geo() {
            return null;
        }

        default List<String> routeGroups() {
            return List.of();
        }
    }

    public static final class Builder {
        private CompiledPolicySet policySet;
        private String policyPath;
        private InputStream policyStream;
        private String secret = "fluxgate";
        private int shardCapacity = 65_536;
        private int sketchDepth = 4;
        private int sketchWidth = 1 << 16;
        private Duration rotationPeriod = Duration.ofSeconds(1);

        public Builder withPolicies(Collection<LimitPolicy> policies) {
            this.policySet = CompiledPolicySet.of(policies);
            return this;
        }

        public Builder withPolicySet(CompiledPolicySet policySet) {
            this.policySet = Objects.requireNonNull(policySet, "policySet");
            return this;
        }

        public Builder withConfig(Path path) {
            this.policyPath = path.toString();
            return this;
        }

        public Builder withConfig(InputStream stream) {
            this.policyStream = stream;
            return this;
        }

        public Builder withSecret(String secret) {
            this.secret = Objects.requireNonNull(secret, "secret");
            return this;
        }

        public Builder withShardCapacity(int shardCapacity) {
            this.shardCapacity = shardCapacity;
            return this;
        }

        public Builder withSketch(int depth, int width) {
            this.sketchDepth = depth;
            this.sketchWidth = width;
            return this;
        }

        public Builder withRotationPeriod(Duration period) {
            this.rotationPeriod = period;
            return this;
        }

        public FluxGate build() {
            return new FluxGate(this);
        }
    }
}
