FluxGate benchmarks
===================

Purpose
-------
This module contains a small set of JMH micro-benchmarks that exercise the FluxGate limiter
hot/cold paths. The harness is designed to be:

- Deterministic (seeded RNG) so runs are repeatable.
- Configurable via `@Param` fields so you can sweep sketch/shard/hot-keys sizes.
- Able to run an optional verification mode that keeps exact counts (for computing
  promotion precision and tier-B approximation error).

What is measured
----------------
The JMH benchmarks exercise two main paths:

- `allowHotPath` — requests sampled from a small "hot" subset (stress promotions / hot path).
- `blockColdPath` — requests sampled from the larger cold keyspace (miss/insert path).

Benchmarks use JMH `Mode.SampleTime` (latency samples) and `Mode.Throughput` (ops/sec). The
`collectStats()` tearDown prints a minimal summary (heap usage) and, when verification is
enabled, the ground-truth top-K keys.

Key knobs (params)
-------------------
The benchmark exposes several `@Param` fields you can set at runtime with `-p`:

- `sketchDepth` (int) — depth of the CountMinLog sketch. Default: 4
- `sketchBuckets` (int) — width/buckets of the sketch. Default: 512
- `shardCapacity` (int) — Tier A shard capacity for the hybrid hot-key cache. Default: 128
- `hotFraction` (double) — fraction of the key-space that is considered "hot". Default: 0.01
- `keySpace` (int) — number of unique keys in the workload. Default: 10000
- `seed` (string / long) — RNG seed for deterministic runs. Default: `123456`
- `verify` (boolean string) — when `true` the harness keeps an exact counter map and prints
  top-K ground truth at the end. Default: `false` (recommended for high-throughput runs)

How to build
------------
From the repository root:

```bash
./gradlew :benchmarks:jmhJar
```

This creates a JMH runnable Jar under `benchmarks/build/libs/` (name will include the
version, e.g. `benchmarks-0.1.0-jmh.jar`).

Quick run examples
------------------
- Latency/throughput run (verify disabled):

```bash
java -jar benchmarks/build/libs/benchmarks-0.1.0-jmh.jar \
  -wi 5 -i 5 -f 2 -t 4 \
  -p verify=false \
  -p seed=123456 \
  -p keySpace=10000 \
  -p hotFraction=0.01 \
  -p sketchDepth=4 -p sketchBuckets=512 -p shardCapacity=128
```

- Run with verification (collect exact counts, prints top-K ground truth). This costs CPU
  and memory, so use it for correctness/precision experiments (not raw throughput):

```bash
java -jar benchmarks/build/libs/benchmarks-0.1.0-jmh.jar \
  -wi 3 -i 3 -f 1 -t 2 \
  -p verify=true \
  -p seed=123456 \
  -p keySpace=20000 \
  -p hotFraction=0.01
```

How to evaluate your goals
--------------------------
Your targets (latency p99, throughput ops/sec, memory, promotion precision, tier-B error,
adaptive convergence) require collecting and post-processing different outputs:

- Latency (p99): use `Mode.SampleTime` samples from JMH. JMH provides sample statistics in
  the report; if you need more accurate percentile computation export samples with
  `-rff <file>` or use the JMH JSON output (`-on newfile.json`) and compute percentiles.

- Throughput (ops/sec): use `Mode.Throughput` and the reported `ops/s` from JMH.
  Increase `-t`/`-threads` to exercise throughput scaling.

- Memory: Runtime heap usage is printed (rough). For rigorous measurement use JVM tools
  (JFR, jcmd, jmap, or `-Xlog:gc*` + `jstat`) and run a long prefill (e.g. 10M unique
  keys) to capture steady-state heap usage.

- Promotion precision: enable `verify=true` and either:
  - Extend `collectStats()` (or add a small post-processing script) to query the limiter
    for which keys are currently "hot" and compare against the ground-truth top-K.
  - Or run the benchmark with `verify=true` and add a small driver that calls the limiter
    `isHot(key)` (see next steps) to compute true-positive / false-positive rates.

- Tier-B error: with `verify=true` compute CountMinLogSketch estimates for top-K keys and
  compare them to exact counts. Report mean/median/95th relative error.

- Adaptive convergence: run multiple independent seeds (set `-p seed=...`), collect the
  adaptive state or final promoted counts, and compute variance across instances.

Notes
-----
- `verify=true` changes the execution profile (adds synchronization and maps) so use it only
  for correctness runs, not for raw throughput or low-latency measurements.
- For sub-microsecond p99 numbers you must run on isolated, dedicated hardware and tune the
  JMH options (`-wi`, `-i`, `-f`, `-t`) and JVM flags. Use `-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints`
  to get more consistent stack traces/profiling if required.

