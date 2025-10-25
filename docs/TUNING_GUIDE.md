# FluxGate tuning guide

FluxGate ships with conservative defaults so that a service can experiment with the
limiter without knowing anything about request volume. When production traffic arrives
it is usually worth adjusting a few knobs so the hot-key cache, probabilistic sketches,
and adaptive controller line up with the workload. This guide calls out the parameters
that have the largest impact and describes how to size them.

## Core limiter sizing

The `FluxGateLimiter.Builder` exposes the same knobs that the public `FluxGate.Builder`
delegates to. Each parameter controls a portion of the hybrid limiter pipeline:

| Setting | Description | Guidance |
| --- | --- | --- |
| `withShardCapacity(int)` | Size of the Tier-A hot key cache. A higher number retains more distinct keys in exact GCRA limiters. | Start with `2 * peakQps` so that every key observed in a 500 ms interval can graduate to Tier-A. Increase if diagnostics show frequent hot key evictions. |
| `withSketch(int depth, int width)` | Shapes the Count-Min Log sketch used for the cold tail. | Depth controls accuracy; width controls memory. For lightly skewed workloads keep the default depth `4` and raise the width to reduce collisions. For extremely heavy long tails, raising depth to `6` improves isolation. |
| `withRotationPeriod(Duration)` | How often the sketch rotates slices to age out stale keys. | Match this period to the SLA you care about. Sub-second APIs benefit from the default `1s`. Slower batch jobs can increase to `5-10s` to retain more history. |
| `withSliceWindow(Duration)` | Total amount of history kept in the sketch before a full reset. | Set to `rotationPeriod * numberOfSlices`. Shorter windows forget cold keys more aggressively; longer windows reduce false positives for rarely-seen keys. |
| `withPolicies(Collection<LimitPolicy>)` / `withPolicySet(CompiledPolicySet)` | Installs precompiled policies. | Compile policies once at startup and reuse the `CompiledPolicySet` across builders so hot reloads do not rebuild matcher tries under load. |

## Adaptive controller knobs

FluxGate continuously adjusts its share of global capacity using EWMA smoothing and the
`LimitScaler`. Most teams only need the defaults, but understanding the knobs helps when
feeding the limiter with cluster-wide estimates:

* `EwmaTrafficEstimator` – `alpha` controls how quickly new samples override history.
  Higher values react faster but make the system more jittery. Leave at the default for
  single-region deployments and raise toward `0.5` when instances frequently join or
  leave the cluster.
* `LimitScaler` – converts observed traffic into the percentage of the global limit that
  belongs to this instance. The scaler expects monotonic permit counts from the estimator.
  Override it when you need deterministic splits (for example, to enforce regional
  quotas).

## Observability hooks

* `FluxGateMetrics` – inject a Micrometer, Dropwizard, or custom sink implementation to
  surface allow/deny counters and adaptive state. The default is a noop emitter.
* `FluxGateStats` – in-memory counters useful for quick diagnostics and tests. Replace
  or augment with your own implementation if you need to export the same data elsewhere.
* `HeatmapReporter` – pairs with the `HeavyKeeper` in Tier-B to print hot-key heatmaps.
  Enable this during load tests to verify that cache sizes match the key distribution.

## Practical workflow

1. Start with defaults and capture telemetry through `FluxGateStats`.
2. Run a load test and inspect the `HeatmapReporter` output to confirm that hot keys
   remain in Tier-A. Increase shard capacity if the same key repeatedly migrates in and
   out of the cache.
3. Use the sketch estimates to ensure the long tail is represented—if not, widen the
   sketch or increase the slice window.
4. When adding cluster-wide QPS hints, lower the EWMA smoothing factor gradually until
   the adaptive share stabilizes without oscillation.

Following this cycle ensures the limiter remains fair while avoiding unnecessary memory
use or elevated latency.
