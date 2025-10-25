# FluxGate examples

The `examples` module bundles small applications that demonstrate how to wire the
limiter into different environments. Each sample is self-contained and runnable through
the Gradle wrapper.

## HTTP quick start

`io.fluxgate.examples.DemoServer` spins up a SparkJava HTTP server and applies FluxGate to
all requests. The `ExampleLauncher` dispatches to it by default, so run it with:

```bash
./gradlew :examples:run --args="server"
```

The entry point uses the default configuration and demonstrates how to guard routes with
the ergonomic `FluxGate` facade. Update the builder to point at a YAML configuration file
and secret before moving to production. The [`tuning guide`](TUNING_GUIDE.md) explains how
to size the cache and sketches once load tests start.

## Resilience4j decorator

FluxGate ships with an adapter that makes it easy to plug the limiter into existing
Resilience4j pipelines. A runnable version of the integration lives in
`io.fluxgate.examples.Resilience4jAdapterExample`, which loads policies from
`examples/config/limits.yaml` and guards a `Supplier`. Execute it with:

```bash
./gradlew :examples:run --args="resilience4j"
```

The high-level flow is:

1. Build or load a `CompiledPolicySet` so incoming requests can be matched once.
2. Compute a deterministic key hash using the same secret that FluxGate uses internally.
3. Cache the matched `LimitPolicy` per request (for example in a `ThreadLocal`) so the
   adapter can look it up from the hash.
4. Let the adapter guard the decorated supplier/function and translate deny decisions
   into `RequestNotPermitted` errors with the retry-after metadata.

A minimal example looks like this:

```java
CompiledPolicySet policies = PolicyCompiler.fromYaml(Path.of("examples/config/limits.yaml"));
FluxGate fluxGate = FluxGate.builder()
        .withPolicySet(policies)
        .withSecret("service-secret")
        .build();
FluxGateLimiter limiter = fluxGate.limiter();
ThreadLocal<Map<Long, LimitPolicy>> policyCache = ThreadLocal.withInitial(HashMap::new);
Resilience4jAdapter adapter = new Resilience4jAdapter(limiter, key -> {
    try {
        return policyCache.get().get(key);
    } finally {
        policyCache.get().remove(key);
    }
});

Supplier<String> supplier = () -> "payload";
RateLimiter rateLimiter = RateLimiterRegistry.ofDefaults().rateLimiter("fluxgate");
Supplier<String> guarded = RateLimiter.decorateSupplier(rateLimiter, () -> {
    PolicyContext ctx = new PolicyContext(clientIp, route, attributes);
    LimitPolicy policy = policies.firstMatch(ctx).orElse(null);
    if (policy == null) {
        return supplier.get();
    }
    long keyHash = KeyBuilder.of()
            .ip(clientIp)
            .route(route)
            .attributes(attributes)
            .buildHash("service-secret");
    policyCache.get().put(keyHash, policy);
    if (!adapter.tryAcquire(keyHash)) {
        Duration retryAfter = adapter.retryAfter(keyHash);
        throw new RequestNotPermitted("Retry after " + retryAfter);
    }
    return supplier.get();
});
```

The sample stores the matched policy in a `ThreadLocal` so both `tryAcquire` and
`retryAfter` can access it without recomputing the decision. Production services often
wrap this logic in a small helper that accepts a request context and returns a decorated
Supplier/Callable.

## Inspecting limiter state

During development, run the heatmap reporter to observe the hybrid cache in action:

```java
HeatmapReporter reporter = new HeatmapReporter(fluxGate.limiter());
reporter.dumpHotKeys(System.nanoTime());
```

The reporter highlights keys that spent time in Tier-A along with the sketch estimate for
their long-tail activity.
