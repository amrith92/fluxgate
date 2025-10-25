# Algorithmic Design Deep Dive

FluxGate enforces rate limits through two cooperating tiers that balance precision and scalability. This document describes the request lifecycle, the data structures that back each tier, and the adaptive controls that keep enforcement consistent under changing load.

## Request journey

Every decision starts at the policy compiler. YAML rules are flattened into a matcher graph composed of prefix tries for network ranges, wildcard-aware trees for routes, and attribute predicates. The graph is precomputed so that the runtime only performs pointer walks without allocating. Incoming requests are wrapped in lightweight contexts that expose IP, route, and arbitrary attributes. These fields are combined with a per-service secret to produce a stable 64-bit fingerprint, which anchors accounting in both tiers.

Once the fingerprint is available, the runtime chooses a control strategy. A tier manager keeps a hot cache for dominant keys and a probabilistic sketch for the long tail. The manager updates both views on every request so that a key can move fluidly between tiers as traffic fluctuates.

## Tier A — precise guardianship

The hot tier focuses on keys that dominate throughput. Admission begins with a probation ring buffer that filters single-hit noise before keys enter the main cache. Residency is governed by a TinyLFU-style frequency sketch that approximates recent popularity with minimal memory. When a key survives the probation period and earns enough frequency, it is granted a dedicated exact limiter.

Each exact limiter implements the Generalized Cell Rate Algorithm with lock-free compare-and-swap loops. The limiter stores the theoretical next-allowed arrival time in nanoseconds. On every request it subtracts the current time, decides whether the new arrival fits inside the configured burst envelope, and either grants the request or returns a precise retry-after interval. Because the state lives in a single atomic primitive, hot keys can be updated concurrently without global locks.

## Tier B — probabilistic stewardship

Keys that remain outside the hot cache are tracked by a count-min sketch with logarithmic counters. The sketch is laid out in slices; each slice covers a time window, and writes lazily reset stale cells the first time a request enters a fresh window. A rotor periodically advances the active slice to prevent counts from accumulating indefinitely. This approach ensures the sketch approximates request volume while keeping memory bounded.

Detecting heavy hitters relies on a companion structure inspired by HeavyKeeper. Each request hashes into candidate slots that remember the currently suspected key and a decaying score. Matching requests refresh the score; mismatches decay it until a new candidate displaces the old one. When the score crosses a threshold, the key is promoted into Tier A. The same structure can emit ranked lists that feed heatmap diagnostics.

## Adaptive quota sharing

FluxGate often operates alongside other instances that share a global budget. To stay fair, every instance maintains an exponentially weighted moving average of observed queries per second. Optional gossip feeds provide cluster-wide estimates. A scaling component multiplies the configured global limit by the local share and clamps the result so that every instance receives at least a trickle of capacity. The runtime records the derived limit, the observed load, and the moving average so operators can reason about how quotas evolve over time.

## Concurrency posture

Hot-tier caches rely on short synchronized sections to protect eviction metadata, but the critical region is small because eviction samples only touch a handful of candidates. Exact limiters, sketches, and adaptive estimators all use atomic primitives to avoid blocking. Rotations in the sketch and adaptive state snapshots run under single-writer guards, ensuring only one thread pays the cost of resetting counters. The resulting pipeline keeps latency predictable even when multiple threads hammer the same keys.

## Observability thread

FluxGate emits events at two layers. Immediate decisions (allowed, throttled, retry-after) funnel into an abstract metrics interface so integrators can bridge into Micrometer, Dropwizard, or bespoke sinks. In parallel, an in-memory statistics view accumulates counters, the latest adaptive scaling state, and the current heavy-hitter list. Operators combine both streams to diagnose policy behavior, verify cache residency, and tune limits without touching the hot path.

## Future explorations

The current design emphasizes local decision making. Future work could explore optional replication of sketch deltas, richer adaptive inputs such as downstream saturation, or pluggable eviction strategies that adapt to bursty tenants. These enhancements would extend the existing architecture without rewriting the hot path or policy compiler.
