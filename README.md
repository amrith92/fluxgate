# FluxGate

FluxGate is an adaptive, in-memory rate limiting library for Java microservices. It combines precise per-key control with approximate long-tail accounting so that services can enforce fairness without an external data store. The repository contains the core limiter engine, a small public API facade, integrations, and runnable examples.

## Overview

FluxGate keeps decisions local to the JVM. Incoming requests are evaluated against compiled policies, hashed into deterministic keys, and routed to a hybrid enforcement pipeline. Hot keys graduate into an exact limiter while colder traffic is tracked probabilistically. Every component is implemented in plain Java with minimal dependencies so the library can be embedded into latency-sensitive services.

### Architecture at a glance

FluxGate begins every decision by compiling YAML policies into a static matcher graph. `PolicyCompiler` maps CIDR ranges into a `PatriciaTrie`, compresses HTTP routes into a wildcard-aware `RouteTrie`, and rewrites attribute predicates so the generated `CompiledPolicySet` can walk through requests with no allocations. The API layer feeds each request through `KeyBuilder`, which folds caller attributes, network coordinates, and a shared secret into a stable 64-bit hash. Once a hash exists, `FluxGateLimiter` steers it into the tiered pipeline.

The first tier is a hot key cache shaped by `HybridHotKeyCache`. A probation ring soaks up one-off traffic while a TinyLFU-style frequency sketch decides when a key deserves residency in the main heap. Once promoted, the key gains its own lock-free `GcraLimiter`, which maintains theoretical arrival times in an `AtomicLong` and computes precise retry-after values when bursts exceed policy. The second tier retains the long tail. `CountMinLogSketch` tracks approximate counters for dormant keys, `HeavyKeeper` spots emerging heavy hitters, and `SliceRotator` rewinds sketch slices to keep stale counts from polluting the estimate.

Adaptive limit sharing closes the loop. Observed local throughput is smoothed by `EwmaTrafficEstimator`, combined with optional cluster-wide hints, and fed into `LimitScaler`, which derives the instance’s share of a global limit. Decisions flow outward through `FluxGateMetrics` and in-memory snapshots in `FluxGateStats`, while `HeatmapReporter` turns heavy-hitter telemetry into plain-text heatmaps for quick inspection.

All of the pieces above are orchestrated by `FluxGateLimiter`, which is wrapped by the `FluxGate` facade exposed from the `api` module. The adapter layer currently ships with a Resilience4j-style decorator so that applications can insert FluxGate inside existing resilience pipelines.

## Module layout

FluxGate’s Gradle project mirrors the runtime architecture. The `core` module carries the limiter engine, probabilistic data structures, policy compiler, adaptive scaler, and observability hooks. The `api` module narrows the surface to a few public entry points—`FluxGate`, `RateLimitResult`, `RetryAfter`, and `ConfigLoader`—so application code depends on a small, stable set of types. Integration adapters live under `adapters`, with the Resilience4j module providing a drop-in decorator for teams already using that ecosystem. Runnable guides sit in `examples`, and regression coverage is concentrated under `test`.

## Getting started

Add FluxGate to your service by constructing a `FluxGate` instance during application startup:

```java
FluxGate limiter = FluxGate.builder()
        .withConfig(Path.of("config/limits.yaml"))
        .withSecret("service-specific-secret")
        .withShardCapacity(32_768)
        .build();

FluxGate.RequestContext ctx = new FluxGate.RequestContext() {
    @Override
    public String ip() {
        return clientIp;
    }

    @Override
    public String route() {
        return requestPath;
    }

    @Override
    public Map<String, String> attributes() {
        return Map.of("userTier", tier);
    }
};

RateLimitResult result = limiter.check(ctx);
if (!result.isAllowed()) {
    return Response.status(429)
            .header("Retry-After", result.retryAfter().seconds())
            .build();
}
```

The builder accepts configuration from a file path, an input stream, or an already compiled `CompiledPolicySet`. Supplying a unique secret ensures keys remain stable even if attribute ordering changes.

### Policy configuration

Policies live in YAML under a top-level `policies` array. Each entry declares an identifier, the desired rate and burst, and a matcher block that combines attributes, routes, and IP ranges into a decision tree:

```yaml
policies:
  - id: checkout-ip
    limitPerSecond: 200
    burst: 400
    windowSeconds: 60
    match:
      ip:
        - 10.0.0.0/8
        - 203.0.113.24
  - id: premium-users
    limitPerSecond: 500
    burst: 600
    match:
      all:
        - route: "/checkout/**"
        - attribute:
            name: userTier
            anyOf: [premium, enterprise]
```

Match expressions support logical composition via `all`, `any`, and `not`. Specialized matchers cover CIDR ranges (`ip`), wildcard-aware route patterns (`route`), and exact or set membership filters on arbitrary attributes. Policies are evaluated in the order they are declared; the first match wins during enforcement, while `evaluatePolicies` exposes full decision traces for diagnostics.

### Adaptive limits and observability

FluxGate can adapt global limits to the local traffic profile by ingesting optional cluster-wide QPS estimates. The `EwmaTrafficEstimator` smooths samples with an exponentially weighted moving average, and `LimitScaler` multiplies the configured limit by the computed share. Results are emitted through the `FluxGateMetrics` interface and mirrored in the `FluxGateStats` in-memory counters so that services can export metrics through Micrometer, Dropwizard, or custom sinks. For quick troubleshooting, instantiate a `HeatmapReporter` with the limiter’s `HeavyKeeper` instance to inspect the most active keys.

## Building and running tests

FluxGate uses the Gradle wrapper checked into the repository. The following commands compile the project and execute the test suite:

```
./gradlew build
./gradlew test
```

The build targets Java 17 and enables incremental compilation, code coverage (JaCoCo), and Checkstyle for static analysis.

## Documentation and further reading

See [`docs/ALGORITHMIC_DESIGN.md`](docs/ALGORITHMIC_DESIGN.md) for a deeper dive into the caches, sketches, and concurrency model that underpin FluxGate. Benchmarks and example deployments under `benchmarks/` and `examples/` illustrate how to size caches and interpret limiter telemetry in production environments.
