package io.fluxgate.core.adaptive;

import java.util.Objects;

public final class LimitScaler {

    public double scale(double globalLimit, EwmaTrafficEstimator.AdaptiveState state) {
        Objects.requireNonNull(state, "state");
        double share = state.share();
        if (share <= 0d) {
            return Math.max(1d, globalLimit);
        }
        return Math.max(1d, globalLimit * share);
    }

    /**
     * Legacy helper for callers that still rely on the scalar share factor.
     */
    public double scale(double globalLimit, double instanceShare) {
        if (instanceShare <= 0d) {
            return Math.max(1d, globalLimit);
        }
        return Math.max(1d, globalLimit * instanceShare);
    }
}
