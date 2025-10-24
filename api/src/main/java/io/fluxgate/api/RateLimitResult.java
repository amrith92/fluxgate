package io.fluxgate.api;

public record RateLimitResult(boolean allowed, RetryAfter retryAfter) {

    public static RateLimitResult allowed() {
        return new RateLimitResult(true, RetryAfter.zero());
    }

    public static RateLimitResult blocked(RetryAfter retryAfter) {
        return new RateLimitResult(false, retryAfter);
    }
}
