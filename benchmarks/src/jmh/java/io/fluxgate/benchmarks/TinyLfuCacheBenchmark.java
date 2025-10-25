package io.fluxgate.benchmarks;

import io.fluxgate.core.tierA.TinyLfuCache;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ThreadLocalRandom;

@State(Scope.Benchmark)
public class TinyLfuCacheBenchmark {

    private TinyLfuCache<Long, Long> cache;
    private long[] hotKeys;
    private int index;

    @Setup(Level.Trial)
    public void setup() {
        cache = new TinyLfuCache<>(1 << 12);
        hotKeys = new long[256];
        for (int i = 0; i < hotKeys.length; i++) {
            long key = i;
            hotKeys[i] = key;
            cache.getOrCompute(key, () -> key);
        }
    }

    @Setup(Level.Iteration)
    public void resetIteration() {
        index = 0;
    }

    @Benchmark
    public void hotKeyHit(Blackhole blackhole) {
        long key = hotKeys[index++ & (hotKeys.length - 1)];
        blackhole.consume(cache.getOrCompute(key, () -> key));
    }

    @Benchmark
    public void coldKeyMiss(Blackhole blackhole) {
        long key = ThreadLocalRandom.current().nextLong(10_000L, 20_000L);
        blackhole.consume(cache.getOrCompute(key, () -> key));
    }
}
