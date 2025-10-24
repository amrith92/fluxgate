# FluxGate

FluxGate is an adaptive, in-memory rate limiting library for Java microservices. It combines precise per-key control with approximate long-tail accounting so that services can enforce fairness without an external data store. This repository contains the core library modules, the public API facade, an example server, and supporting tests and benchmarks.

## Motivation and capabilities

Applications that depend on distributed rate limiters often struggle with network hops, cross-region coordination, and hot key protection. FluxGate addresses these concerns by keeping decision logic local to the JVM. The library scales to millions of distinct keys per minute by classifying traffic into hot and cold paths. Hot keys receive exact enforcement through a lock free implementation of the Generalized Cell Rate Algorithm while colder keys flow through a probabilistic sketch backed by a heavy hitter detector. The result is consistent single digit microsecond latency and high precision on the keys that matter.

FluxGate ships with a policy compiler for attribute based matching, adaptive scaling that proportionally divides shared quotas across instances, and integration hooks that mirror popular frameworks such as resilience4j. Observability is embedded from the start, with metrics, heatmaps, and saturation tracking exposed through simple interfaces.

## Project layout

The Gradle build produces multiple modules aligned with the runtime components:

- `core`: Internal rate limiting engines, caches, adaptive scaling, and observability primitives.
- `api`: Public entry points used by service integrations, including configuration loading and response types.
- `examples`: A lightweight HTTP server that demonstrates end-to-end usage of the limiter.
- `test`: Unit and integration tests that validate Tier A precision, Tier B accuracy, and policy compilation.

Each module is organized under the `io.fluxgate` package hierarchy for straightforward dependency management.

## Building and running tests

FluxGate uses the Gradle wrapper checked into the repository. The following commands compile the project and execute the test suite:

```
./gradlew build
./gradlew test
```

The build depends on the standard Gradle toolchain and a Java 17 compatible runtime. All tasks are cache friendly and configured for incremental execution. The GitHub Actions workflow located under `.github/workflows/ci.yml` runs the same commands for continuous integration.

## Configuring the limiter

Policies are defined in YAML and compiled into `LimitPolicy` instances. The compiler accepts attribute selectors that can combine IPs, routes, headers, and custom tags. Burst capacity and sustained rate are expressed in requests per second with explicit time windows. Runtime code creates a `FluxGate` instance via the builder, supplying policies, sketch sizing parameters, and shard counts. The API returns a `RateLimitResult` that indicates whether the request is allowed together with an optional `RetryAfter` hint for clients.

FluxGate supports both static configuration files and programmatic policy construction. Reload hooks can instantiate a new limiter and swap it into service with minimal disruption because the implementation maintains lock free data structures per shard.

## Observability and integration

Operators integrate FluxGate with Micrometer, Dropwizard Metrics, or custom monitoring stacks through the `FluxGateMetrics` facade. Reported metrics cover request totals, blocked counts, hot key promotions, sketch saturation, and memory utilization. A dedicated heatmap reporter publishes latency and key distribution statistics so that on-call teams can investigate traffic anomalies quickly.

The library includes adapters for resilience4j style decorators. This allows developers to plug FluxGate into existing circuit breaker pipelines and reuse familiar resilience patterns. The adaptive limit scaler tracks local QPS, compares it to an expected cluster total, and adjusts token generation so that each instance enforces its fair share.

## Documentation and further reading

The `docs` directory houses a detailed algorithmic design deep dive that explains how each component works internally. Benchmark harnesses and stress scenarios provide additional guidance for tuning the limiter in production environments.
