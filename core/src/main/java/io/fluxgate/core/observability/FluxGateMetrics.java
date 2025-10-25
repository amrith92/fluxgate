package io.fluxgate.core.observability;

import io.fluxgate.core.adaptive.EwmaTrafficEstimator;

public interface FluxGateMetrics {

    void recordAllowed();

    void recordBlocked();

    default void recordAdaptiveState(EwmaTrafficEstimator.AdaptiveState state) {
        // no-op by default
    }

    static FluxGateMetrics noop() {
        return new FluxGateMetrics() {
            @Override
            public void recordAllowed() {
                // do nothing
            }

            @Override
            public void recordBlocked() {
                // do nothing
            }

            @Override
            public void recordAdaptiveState(EwmaTrafficEstimator.AdaptiveState state) {
                // do nothing
            }
        };
    }
}
