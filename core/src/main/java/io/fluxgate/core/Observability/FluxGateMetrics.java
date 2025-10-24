package io.fluxgate.core.Observability;

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
