package io.fluxgate.core.observability;

import io.fluxgate.core.adaptive.EwmaTrafficEstimator;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class FluxGateStats {

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong blockedRequests = new AtomicLong();
    private final AtomicReference<EwmaTrafficEstimator.AdaptiveState> adaptiveState =
            new AtomicReference<>();

    public void onAllowed() {
        totalRequests.incrementAndGet();
    }

    public void onBlocked() {
        blockedRequests.incrementAndGet();
        totalRequests.incrementAndGet();
    }

    public void onAdaptiveUpdate(EwmaTrafficEstimator.AdaptiveState state) {
        adaptiveState.set(state);
    }

    public long totalRequests() {
        return totalRequests.get();
    }

    public long blockedRequests() {
        return blockedRequests.get();
    }

    public EwmaTrafficEstimator.AdaptiveState adaptiveState() {
        return adaptiveState.get();
    }

    public Map<String, Double> adaptiveDebugView() {
        EwmaTrafficEstimator.AdaptiveState state = adaptiveState.get();
        if (state == null) {
            return Map.of();
        }
        return state.debugView();
    }
}
