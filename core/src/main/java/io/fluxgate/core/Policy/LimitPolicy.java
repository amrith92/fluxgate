package io.fluxgate.core.Policy;

public record LimitPolicy(String id, double limitPerSecond, double burstTokens, long windowSeconds) {
}
