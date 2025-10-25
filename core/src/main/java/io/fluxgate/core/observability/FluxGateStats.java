package io.fluxgate.core.observability;

import java.util.concurrent.atomic.AtomicLong;

public final class FluxGateStats {

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong blockedRequests = new AtomicLong();

    public void onAllowed() {
        totalRequests.incrementAndGet();
    }

    public void onBlocked() {
        blockedRequests.incrementAndGet();
        totalRequests.incrementAndGet();
    }

    public long totalRequests() {
        return totalRequests.get();
    }

    public long blockedRequests() {
        return blockedRequests.get();
    }
}
