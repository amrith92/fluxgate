package io.fluxgate.core.policy;

public record LimitPolicy(String id, double limitPerSecond, double burstTokens, long windowSeconds) {
}
