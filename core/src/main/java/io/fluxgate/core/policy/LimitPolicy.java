package io.fluxgate.core.policy;

public record LimitPolicy(String id,
                          double limitPerSecond,
                          double burstTokens,
                          long windowSeconds,
                          PolicyMatcher matcher) {

    public LimitPolicy {
        matcher = matcher == null ? PolicyMatcher.always() : matcher;
    }

    public LimitPolicy(String id, double limitPerSecond, double burstTokens, long windowSeconds) {
        this(id, limitPerSecond, burstTokens, windowSeconds, PolicyMatcher.always());
    }
}
