package io.fluxgate.api;

import java.time.Duration;

public record RetryAfter(Duration duration) {

    public static RetryAfter zero() {
        return new RetryAfter(Duration.ZERO);
    }

    public static RetryAfter ofSeconds(long seconds) {
        return new RetryAfter(Duration.ofSeconds(seconds));
    }

    public static RetryAfter ofNanos(long nanos) {
        return new RetryAfter(Duration.ofNanos(nanos));
    }

    public long seconds() {
        return duration.getSeconds();
    }
}
