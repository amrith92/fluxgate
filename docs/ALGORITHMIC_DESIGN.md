# Algorithmic Design Deep Dive

FluxGate enforces rate limits through two cooperating tiers that balance precision and scalability. This document describes the data structures, scheduling strategy, and concurrency guarantees that allow the limiter to sustain high request volumes with bounded latency.

## Tier A hot key control

Tier A stores keys that receive a large share of traffic. Each shard keeps a segmented LRU cache backed by a hybrid admission policy that combines a TinyLFU-inspired frequency sketch with a probationary FIFO window. The first time a key arrives it enters the probation window. A follow-up hit or a sufficiently high sketch score promotes the key into the precise GCRA cache. This two-phase approach reacts quickly to bursts while still filtering one-hit traffic. Eviction samples a handful of candidates and removes the entry with the weakest combined frequency and recency score so that cold keys fall out promptly while hot keys remain protected.

The Generalized Cell Rate Algorithm models traffic as a virtual departure schedule. Every key maintains a theoretical arrival time that advances by the token period. Requests succeed when the current time plus burst allowance exceeds the stored arrival time. FluxGate implements this logic with compare-and-set loops on padded atomic longs. Shards map directly to CPU cores so that no two threads contend for the same atomic variable. The limiter returns a retry hint that equals the difference between the scheduled time and the current moment when a request is rejected.

Tier A rotation relies on periodic maintenance tasks that prune expired entries. The segmented cache records the last access timestamp for each segment. Background threads sweep the coolest segment first, which minimizes work during heavy load and avoids pauses on the hot path.

## Tier B long tail accounting

Cold keys bypass the GCRA cache and are recorded in a windowed Count Min Log Sketch. The sketch is organized into multiple slices, each tied to an epoch counter. When a request touches a cell whose epoch is stale, the cell resets lazily before the new count is added. Counters use a logarithmic representation similar to Morris counting so that one byte can represent large values while retaining relative accuracy.

FluxGate applies conservative updates across all hash rows to bound the probability of overcounting. Queries compute the minimum count across the rows that correspond to the key and aggregate results across the slices that fall inside the observation window. The number of slices controls the trade off between accuracy and memory footprint.

A HeavyKeeper structure monitors the same stream to detect emerging hot keys. Each row in the structure maintains a candidate key, a score, and a decay factor. When a key collides with an existing candidate, the score increases; otherwise the score decays and the candidate may be replaced. Keys whose scores cross the promotion threshold migrate into Tier A where they gain precise enforcement.

## Adaptive limit scaling

Distributed deployments often share a global quota. FluxGate adjusts per instance limits using an exponentially weighted moving average of observed QPS. The estimator integrates request counts over a sliding window and smooths spikes to avoid oscillations. The limit scaler multiplies the global limit by the local share of traffic, yielding a target rate for the shard. Because shard adjustments apply during the token refill step, the limiter maintains stability even when the cluster topology changes.

## Concurrency model

Shards isolate state updates to per core arenas. Requests select a shard by hashing the key and taking a modulus of the configured shard count. Within a shard, Tier A entries use atomic operations without locking, and Tier B sketches rely on striped arrays padded to cache line boundaries. Maintenance work such as slice rotation and statistics publishing occurs on dedicated threads that coordinate through lightweight barriers, ensuring that request threads remain wait free.

## Observability pipeline

FluxGate exposes metrics that mirror the structure of the limiter. Counters report total requests, blocked attempts, Tier promotions, and memory consumption. Gauges track sketch saturation and cache occupancy. A heatmap reporter samples request latencies and key distribution so that operators can detect emerging hot spots before they impact customers. The metrics facade intentionally decouples the data model from any particular monitoring framework, allowing adapters to translate the information into Micrometer, Dropwizard, or custom registries.

## Integration guidance

Service owners integrate FluxGate by constructing a `FluxGate` instance during application startup. Policies load from YAML files or structured configuration stores. Incoming requests pass through the limiter before business logic executes. When the limiter denies a request, clients receive a `RetryAfter` value that encodes the wait time in milliseconds. The resilience4j adapter wraps the `FluxGate` check inside familiar decorators so that teams can reuse established resilience patterns without refactoring core logic.

## Future extensions

The current design prioritizes local decision making. Future work can explore optional shared state replication, GPU accelerated sketch updates, or automatic policy tuning based on downstream latency signals. These enhancements would retain the same architecture while expanding the scenarios in which FluxGate operates.
