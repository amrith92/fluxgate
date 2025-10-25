package io.fluxgate.core.adaptive;

public final class LimitScaler {

    public double scale(double globalLimit, double instanceShare) {
        if (instanceShare <= 0) {
            return globalLimit;
        }
        return Math.max(1d, globalLimit * instanceShare);
    }
}
