package io.fluxgate.benchmarks;

import io.fluxgate.core.FluxGateLimiter;
import io.fluxgate.core.observability.FluxGateMetrics;
import io.fluxgate.core.observability.FluxGateStats;
import io.fluxgate.core.policy.LimitPolicy;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.Duration;

@State(Scope.Benchmark)
public class FluxGateLimiterBenchmark {

    private FluxGateLimiter limiter;
    private LimitPolicy allowPolicy;
    private LimitPolicy blockPolicy;
    private long allowKey;
    private long blockKey;
    private long allowTime;

    @Setup(Level.Trial)
    public void setup() {
        limiter = FluxGateLimiter.builder()
                .withMetrics(FluxGateMetrics.noop())
                .withStats(new FluxGateStats())
                .withShardCapacity(128)
                .withSketch(4, 512)
                .withRotationPeriod(Duration.ofSeconds(1))
                .build();
        allowPolicy = new LimitPolicy("allow", 10_000d, 10_000d, 60);
        blockPolicy = new LimitPolicy("block", 1d, 1d, 60);
        allowKey = 1L;
        blockKey = 2L;
    }

    @Setup(Level.Iteration)
    public void resetIteration() {
        allowTime = 0L;
        limiter.check(blockKey, ignored -> blockPolicy, 0L);
        limiter.check(blockKey, ignored -> blockPolicy, 0L);
    }

    @Benchmark
    public void allowHotPath(Blackhole blackhole) {
        allowTime += 1_000_000L;
        FluxGateLimiter.RateLimitOutcome outcome = limiter.check(allowKey, ignored -> allowPolicy, allowTime);
        blackhole.consume(outcome);
    }

    @Benchmark
    public void blockColdPath(Blackhole blackhole) {
        FluxGateLimiter.RateLimitOutcome outcome = limiter.check(blockKey, ignored -> blockPolicy, 0L);
        blackhole.consume(outcome);
    }
}
