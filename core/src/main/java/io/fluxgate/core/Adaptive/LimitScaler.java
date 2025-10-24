package io.fluxgate.core.Adaptive;

public final class LimitScaler {

    public double scale(double globalLimit, double instanceShare) {
        if (instanceShare <= 0) {
            return globalLimit;
        }
        return Math.max(1d, globalLimit * instanceShare);
    }
}
