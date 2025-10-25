package io.fluxgate.benchmarks;

import io.fluxgate.core.FluxGateLimiter;
import io.fluxgate.core.observability.FluxGateMetrics;
import io.fluxgate.core.observability.FluxGateStats;
import io.fluxgate.core.policy.LimitPolicy;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

@State(Scope.Benchmark)
@Fork(value = 2, jvmArgsAppend = {"-Xms512m", "-Xmx2g", "-XX:+UseG1GC"})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode({Mode.SampleTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FluxGateLimiterBenchmark {

    // --- configurable knobs to sweep trade-offs ---
    @Param({"4"})
    private int sketchDepth;

    @Param({"512"})
    private int sketchBuckets;

    @Param({"128"})
    private int shardCapacity;

    // fraction of keys that are "hot"
    @Param({"0.01"})
    private double hotFraction;

    // total unique keys in the workload
    @Param({"10000"})
    private int keySpace;

    // RNG seed for reproducible runs; expose as string to allow empty/default if desired
    @Param({"123456"})
    private String seed;

    // Optional verification mode (collect exact counts) - keep default off to avoid perturbation
    @Param({"false"})
    private String verify;

    @Param({"false"})
    private String adaptiveCheck;

    // --- core objects used by benchmarks ---
    private FluxGateLimiter limiter;
    private FluxGateStats stats;
    private LimitPolicy allowPolicy;
    private LimitPolicy blockPolicy;

    // deterministic workload state
    private Random rng;
    private long[] keys;
    private int hotCount;
    private int hotKeyBase;
    private int coldKeyBase;
    private long baseTimeNanos;

    // verification structures (optional)
    private boolean verifyEnabled;
    private ConcurrentHashMap<Long, LongAdder> exactCounts;
    // spike/tail detection
    private java.util.concurrent.atomic.LongAdder spikeCounter;
    private ConcurrentHashMap<Long, LongAdder> spikeSamples;
    private long spikeThresholdNanos = 10_000L; // 10 microseconds
    // adaptive state sampling
    private java.util.concurrent.ConcurrentLinkedQueue<Double> adaptiveShares;
    private long sampleCounter;

    @Setup(Level.Trial)
    public void setup() {
        // deterministic RNG for reproducible runs
        long seedLong = 123456L;
        try {
            seedLong = Long.parseLong(seed);
        } catch (NumberFormatException ignore) {
        }
        rng = new Random(seedLong);

        verifyEnabled = Boolean.parseBoolean(verify);
        if (verifyEnabled) {
            exactCounts = new ConcurrentHashMap<>();
            spikeCounter = new java.util.concurrent.atomic.LongAdder();
            spikeSamples = new ConcurrentHashMap<>();
            adaptiveShares = new java.util.concurrent.ConcurrentLinkedQueue<>();
        }

        stats = new FluxGateStats();
        limiter = FluxGateLimiter.builder()
                .withMetrics(FluxGateMetrics.noop())
                .withStats(stats)
                .withShardCapacity(shardCapacity)
                .withSketch(sketchDepth, sketchBuckets)
                .withRotationPeriod(Duration.ofSeconds(1))
                .build();

        allowPolicy = new LimitPolicy("allow", 10_000d, 10_000d, 60);
        blockPolicy = new LimitPolicy("block", 1d, 1d, 60);

        // prepare deterministic keyspace and mark a fraction as hot
        keys = new long[keySpace];
        for (int i = 0; i < keySpace; i++) {
            keys[i] = i + 1L;
        }
        hotCount = Math.max(1, (int) Math.round(keySpace * hotFraction));
        hotKeyBase = 0;
        coldKeyBase = hotCount;

        baseTimeNanos = System.nanoTime();

        // warm up the limiter with a controlled distribution: hot keys get many hits, cold few
        int hotWarmups = Math.max(1_000, hotCount * 10);
        int coldWarmups = Math.max(100, (keySpace - hotCount));

        for (int i = 0; i < hotWarmups; i++) {
            long key = keys[hotKeyBase + (rng.nextInt(hotCount))];
            limiter.check(key, ignored -> allowPolicy, baseTimeNanos + i);
            if (verifyEnabled) {
                exactCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
            }
        }
        for (int i = 0; i < coldWarmups; i++) {
            long key = keys[coldKeyBase + (rng.nextInt(keySpace - hotCount))];
            limiter.check(key, ignored -> blockPolicy, baseTimeNanos + hotWarmups + i);
            if (verifyEnabled) {
                exactCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
            }
        }
    }

    @Setup(Level.Iteration)
    public void resetIteration() {
        // advance the base time slightly each iteration to emulate progression
        baseTimeNanos += TimeUnit.MILLISECONDS.toNanos(10);
    }

    @Benchmark
    @Group("hot")
    public void allowHotPath(Blackhole blackhole) {
        long key = keys[hotKeyBase + (rng.nextInt(hotCount))];
        long now = System.nanoTime() + baseTimeNanos;
        long t0 = System.nanoTime();
        FluxGateLimiter.RateLimitOutcome outcome = limiter.check(key, ignored -> allowPolicy, now);
        long latency = System.nanoTime() - t0;
        if (verifyEnabled && latency > spikeThresholdNanos) {
            spikeCounter.increment();
            spikeSamples.computeIfAbsent(key, k -> new LongAdder()).increment();
        }
        // sample adaptive state occasionally
        if (verifyEnabled && (++sampleCounter % 1000L) == 0L) {
            var state = limiter.adaptiveState(now);
            if (state != null) {
                try {
                    Double share = null;
                    var dv = state.debugView();
                    if (dv != null && dv.containsKey("share")) {
                        share = dv.get("share");
                    }
                    if (share != null) {
                        adaptiveShares.add(share);
                    }
                } catch (Exception ignore) {
                }
            }
        }
        if (verifyEnabled) {
            exactCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
        }
        blackhole.consume(outcome);
    }

    @Benchmark
    @Group("cold")
    public void blockColdPath(Blackhole blackhole) {
        long key = keys[coldKeyBase + (rng.nextInt(keySpace - hotCount))];
        long now = System.nanoTime() + baseTimeNanos;
        long t0 = System.nanoTime();
        FluxGateLimiter.RateLimitOutcome outcome = limiter.check(key, ignored -> blockPolicy, now);
        long latency = System.nanoTime() - t0;
        if (verifyEnabled && latency > spikeThresholdNanos) {
            spikeCounter.increment();
            spikeSamples.computeIfAbsent(key, k -> new LongAdder()).increment();
        }
        if (verifyEnabled && (++sampleCounter % 1000L) == 0L) {
            var state = limiter.adaptiveState(now);
            if (state != null) {
                try {
                    var dv = state.debugView();
                    if (dv != null && dv.containsKey("share")) {
                        adaptiveShares.add(dv.get("share"));
                    }
                } catch (Exception ignore) {
                }
            }
        }
        if (verifyEnabled) {
            exactCounts.computeIfAbsent(key, k -> new LongAdder()).increment();
        }
        blackhole.consume(outcome);
    }

    @TearDown(Level.Trial)
    public void collectStats() {
        // Print a few high-level stats so benchmark logs can be post-processed.
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        StringBuilder sb = new StringBuilder();
        sb.append("FluxGateLimiterBenchmark: finished trial;");
        sb.append(" hotCount=").append(hotCount);
        sb.append(" keySpace=").append(keySpace);
        sb.append(" seed=").append(seed);
        sb.append(" verify=").append(verifyEnabled);
        sb.append(" heapUsedBytes=").append(used);
        System.out.println(sb.toString());

        if (verifyEnabled && exactCounts != null) {
            // compute top-K heavy hitters from ground truth
            int k = 10;
            Comparator<Map.Entry<Long, LongAdder>> comparator = Comparator.comparingLong(e -> e.getValue().longValue());
            PriorityQueue<Map.Entry<Long, LongAdder>> pq = new PriorityQueue<>(comparator);
            for (Map.Entry<Long, LongAdder> e : exactCounts.entrySet()) {
                if (pq.size() < k) {
                    pq.offer(e);
                    continue;
                }
                long val = e.getValue().longValue();
                if (val > pq.peek().getValue().longValue()) {
                    pq.poll();
                    pq.offer(e);
                }
            }
            // extract in descending order into a simple map
            Map<Long, Long> topK = new java.util.LinkedHashMap<>();
            java.util.List<Map.Entry<Long, LongAdder>> list = new java.util.ArrayList<>();
            while (!pq.isEmpty()) {
                list.add(pq.poll());
            }
            list.sort((a, b) -> Long.compare(b.getValue().longValue(), a.getValue().longValue()));
            for (Map.Entry<Long, LongAdder> e : list) {
                topK.put(e.getKey(), e.getValue().longValue());
            }
            System.out.println("FluxGateLimiterBenchmark: top-" + k + " ground-truth keys: " + topK);

            // Compute promotion precision and tier-B error using limiter hooks
            int promoted = 0;
            int trueHot = 0;
            double totalRelativeError = 0.0d;
            int errorCount = 0;
            for (Map.Entry<Long, Long> e : topK.entrySet()) {
                long key = e.getKey();
                long exact = e.getValue();
                boolean isHot = limiter.isHot(key);
                if (isHot) {
                    promoted++;
                }
                trueHot++;
                long estimate = limiter.sketchEstimate(key);
                if (exact > 0) {
                    double rel = Math.abs((double) estimate - (double) exact) / (double) exact;
                    totalRelativeError += rel;
                    errorCount++;
                }
            }
            double promotionPrecision = trueHot == 0 ? 0.0d : (double) promoted / (double) trueHot;
            double meanRelativeError = errorCount == 0 ? 0.0d : totalRelativeError / (double) errorCount;
            // Print CSV-friendly summary
            StringBuilder csv = new StringBuilder();
            csv.append("fluxgate.benchmark.verify,");
            csv.append("promotionPrecision=").append(promotionPrecision).append(",");
            csv.append("meanTierBRelativeError=").append(meanRelativeError).append(",");
            csv.append("promoted=").append(promoted).append(",");
            csv.append("topK=").append(trueHot);
            System.out.println(csv.toString());

            // Also write a structured JSON file for robust downstream parsing; include spike/adaptive metrics.
            try {
                java.nio.file.Path resultsDir = java.nio.file.Paths.get("benchmarks", "results");
                java.nio.file.Files.createDirectories(resultsDir);
                java.nio.file.Path outFile = resultsDir.resolve("verify_" + seed + ".json");
                StringBuilder sbj = new StringBuilder();
                sbj.append('{');
                sbj.append("\"seed\":\"").append(seed).append('\"');
                sbj.append(",\"promotionPrecision\":").append(promotionPrecision);
                sbj.append(",\"meanTierBRelativeError\":").append(meanRelativeError);
                sbj.append(",\"promoted\":").append(promoted);
                sbj.append(",\"topK\":").append(trueHot);
                sbj.append(",\"heapUsedBytes\":").append(used);
                long spikes = spikeCounter == null ? 0L : spikeCounter.longValue();
                sbj.append(",\"spikeCount\":").append(spikes);
                // adaptive share stats
                double adaptiveMean = 0.0d;
                double adaptiveStd = 0.0d;
                int aCount = 0;
                if (adaptiveShares != null) {
                    java.util.List<Double> tmp = new java.util.ArrayList<>();
                    adaptiveShares.forEach(tmp::add);
                    aCount = tmp.size();
                    if (aCount > 0) {
                        double sum = 0.0d;
                        for (double v : tmp) {
                            sum += v;
                        }
                        adaptiveMean = sum / aCount;
                        double var = 0.0d;
                        for (double v : tmp) {
                            var += (v - adaptiveMean) * (v - adaptiveMean);
                        }
                        adaptiveStd = Math.sqrt(var / aCount);
                    }
                }
                sbj.append(",\"adaptiveSampleCount\":").append(aCount);
                sbj.append(",\"adaptiveMean\":").append(adaptiveMean);
                sbj.append(",\"adaptiveStd\":").append(adaptiveStd);
                // optionally perform an adaptive convergence latency check (best-effort)
                boolean doAdaptive = Boolean.parseBoolean(adaptiveCheck);
                int adaptiveLatencyCount = 0;
                double adaptiveLatencyP50 = -1.0d;
                double adaptiveLatencyP95 = -1.0d;
                double adaptiveLatencyP99 = -1.0d;
                if (doAdaptive) {
                    java.util.List<Long> latencies = new java.util.ArrayList<>();
                    try {
                        // perform several independent spike injections and measure latency to observe change
                        for (int round = 0; round < 3; round++) {
                            long start = System.nanoTime();
                            var beforeState = limiter.adaptiveState(start);
                            double beforeShare = 0.0d;
                            if (beforeState != null) {
                                beforeState.debugView();
                                if (beforeState.debugView().containsKey("share")) {
                                    beforeShare = beforeState.debugView().get("share");
                                }
                            }
                            double spikeQps = Math.max(1.0d, Math.abs(beforeShare) * 100.0d + 1.0d);
                            limiter.ingestClusterQps(spikeQps, start);
                            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                            long observed = -1L;
                            while (System.nanoTime() < deadline) {
                                var s = limiter.adaptiveState(System.nanoTime());
                                if (s != null && s.debugView() != null && s.debugView().containsKey("share")) {
                                    double cur = s.debugView().get("share");
                                    double diff = Math.abs(cur - beforeShare);
                                    double thresh = Math.max(0.01d, 0.15d * Math.abs(beforeShare));
                                    if (diff > thresh) {
                                        observed = System.nanoTime() - start;
                                        break;
                                    }
                                }
                                try {
                                    Thread.sleep(5);
                                } catch (InterruptedException ignored) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                            if (observed >= 0L) {
                                latencies.add(TimeUnit.NANOSECONDS.toMillis(observed));
                            }
                            // small pause before next injection to let system stabilize
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    } catch (Exception ignore) {
                        // best-effort only
                    }
                    adaptiveLatencyCount = latencies.size();
                    if (adaptiveLatencyCount > 0) {
                        latencies.sort(Long::compare);
                        adaptiveLatencyP50 = latencies.get((int) Math.floor(0.5 * (adaptiveLatencyCount - 1)));
                        adaptiveLatencyP95 = latencies.get((int) Math.floor(0.95 * (adaptiveLatencyCount - 1)));
                        adaptiveLatencyP99 = latencies.get((int) Math.floor(0.99 * (adaptiveLatencyCount - 1)));
                    }
                }
                sbj.append(",\"topKList\":{");
                boolean first = true;
                for (Map.Entry<Long, Long> e2 : topK.entrySet()) {
                    if (!first) {
                        sbj.append(',');
                    }
                    sbj.append('"').append(e2.getKey()).append('"').append(':').append(e2.getValue());
                    first = false;
                }
                sbj.append('}');
                // append adaptive quantiles
                sbj.append(',').append("\"adaptiveLatencyCount\":").append(adaptiveLatencyCount);
                sbj.append(',').append("\"adaptiveLatencyP50\":").append(adaptiveLatencyP50);
                sbj.append(',').append("\"adaptiveLatencyP95\":").append(adaptiveLatencyP95);
                sbj.append(',').append("\"adaptiveLatencyP99\":").append(adaptiveLatencyP99);
                sbj.append('}');
                java.nio.file.Files.writeString(outFile, sbj.toString());
            } catch (Exception ignore) {
                // Best-effort only
            }
        }
    }
}
