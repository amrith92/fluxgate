package io.fluxgate.core.Adaptive;

import java.util.concurrent.atomic.AtomicLong;

public final class EwmaTrafficEstimator {

    private static final double ALPHA = 0.2d;
    private final AtomicLong qpsEstimate = new AtomicLong(1L);

    public void record(long permits) {
        long prev = qpsEstimate.get();
        long next = (long) (ALPHA * permits + (1 - ALPHA) * prev);
        qpsEstimate.set(Math.max(1L, next));
    }

    public double instanceShare() {
        return qpsEstimate.get();
    }
}
