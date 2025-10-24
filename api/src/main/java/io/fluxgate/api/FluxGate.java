package io.fluxgate.api;

import io.fluxgate.core.FluxGateLimiter;
import io.fluxgate.core.Policy.KeyBuilder;
import io.fluxgate.core.Policy.LimitPolicy;
import io.fluxgate.core.Policy.PolicyCompiler;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class FluxGate {

    private final FluxGateLimiter limiter;
    private final Function<Long, LimitPolicy> policyLookup;
    private final String secret;

    private FluxGate(Builder builder) {
        this.secret = builder.secret;
        Collection<LimitPolicy> policies = builder.policies;
        if (policies == null) {
            if (builder.policyPath != null) {
                policies = PolicyCompiler.fromYaml(Path.of(builder.policyPath));
            } else if (builder.policyStream != null) {
                policies = PolicyCompiler.fromYaml(builder.policyStream);
            } else {
                policies = PolicyCompiler.defaults();
            }
        }
        this.policyLookup = key -> policies.stream().findFirst().orElse(null);
        this.limiter = FluxGateLimiter.builder()
                .withPolicies(policies)
                .withShardCapacity(builder.shardCapacity)
                .withSketch(builder.sketchDepth, builder.sketchWidth)
                .withRotationPeriod(builder.rotationPeriod)
                .build();
    }

    public RateLimitResult check(RequestContext ctx) {
        long keyHash = KeyBuilder.of()
                .ip(ctx.ip())
                .route(ctx.route())
                .buildHash(secret);
        FluxGateLimiter.RateLimitOutcome outcome = limiter.check(keyHash, policyLookup, System.nanoTime());
        if (outcome.allowed()) {
            return RateLimitResult.allowed();
        }
        return RateLimitResult.blocked(RetryAfter.ofNanos(outcome.retryAfterNanos()));
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
    }

    public static final class Builder {
        private Collection<LimitPolicy> policies;
        private String policyPath;
        private InputStream policyStream;
        private String secret = "fluxgate";
        private int shardCapacity = 65_536;
        private int sketchDepth = 4;
        private int sketchWidth = 1 << 16;
        private Duration rotationPeriod = Duration.ofSeconds(1);

        public Builder withPolicies(Collection<LimitPolicy> policies) {
            this.policies = policies;
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
