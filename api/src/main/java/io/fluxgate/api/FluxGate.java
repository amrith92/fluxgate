package io.fluxgate.api;

import io.fluxgate.core.FluxGateLimiter;
import io.fluxgate.core.policy.CompiledPolicySet;
import io.fluxgate.core.policy.KeyBuilder;
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
        PolicyContext context = new PolicyContext(ctx.ip(), ctx.route(), attributes);
        LimitPolicy policy = policySet.firstMatch(context).orElse(null);
        long keyHash = KeyBuilder.of()
                .ip(ctx.ip())
                .route(ctx.route())
                .attributes(attributes)
                .buildHash(secret);
        FluxGateLimiter.RateLimitOutcome outcome = limiter.check(keyHash, ignored -> policy, System.nanoTime());
        if (outcome.allowed()) {
            return RateLimitResult.allowed();
        }
        return RateLimitResult.blocked(RetryAfter.ofNanos(outcome.retryAfterNanos()));
    }

    public List<PolicyDecision> evaluatePolicies(RequestContext ctx) {
        PolicyContext context = new PolicyContext(ctx.ip(), ctx.route(), ctx.attributes());
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
