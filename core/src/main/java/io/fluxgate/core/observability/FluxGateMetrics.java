package io.fluxgate.core.observability;

public interface FluxGateMetrics {

    void recordAllowed();

    void recordBlocked();

    static FluxGateMetrics noop() {
        return new FluxGateMetrics() {
            @Override
            public void recordAllowed() {}

            @Override
            public void recordBlocked() {}
        };
    }
}
