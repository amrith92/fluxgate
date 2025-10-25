package io.fluxgate.examples;

import io.fluxgate.api.FluxGate;
import io.fluxgate.api.RateLimitResult;
import spark.Service;

public final class DemoServer {

    public static void main(String[] args) {
        FluxGate limiter = FluxGate.builder()
                // See docs/TUNING_GUIDE.md for sizing guidance on cache and sketch options.
                .withSecret("demo-secret")
                .withShardCapacity(32_768)
                .withSketch(4, 1 << 15)
                .build();
        Service http = Service.ignite().port(8080);
        http.before((request, response) -> {
            RateLimitResult result = limiter.check(new FluxGate.RequestContext() {
                @Override
                public String ip() {
                    return request.ip();
                }

                @Override
                public String route() {
                    return request.pathInfo();
                }
            });
            if (!result.isAllowed()) {
                response.status(429);
                response.header("Retry-After", String.valueOf(result.retryAfter().seconds()));
                halt(429, "Too many requests\n");
            }
        });
        http.get("/*", (request, response) -> "OK\n");
    }

    private static void halt(int status, String body) {
        throw halt(status, body, null);
    }

    private static RuntimeException halt(int status, String body, Throwable cause) {
        return new RuntimeException("HTTP " + status + ": " + body, cause);
    }
}
