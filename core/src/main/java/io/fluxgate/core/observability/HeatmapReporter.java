package io.fluxgate.core.observability;

import io.fluxgate.core.tierB.HeavyKeeper;

public final class HeatmapReporter {

    private final HeavyKeeper heavyKeeper;

    public HeatmapReporter(HeavyKeeper heavyKeeper) {
        this.heavyKeeper = heavyKeeper;
    }

    public String renderTopKeys() {
        StringBuilder builder = new StringBuilder();
        for (HeavyKeeper.Entry entry : heavyKeeper.topK()) {
            builder.append(Long.toHexString(entry.key())).append(':').append(entry.count()).append('\n');
        }
        return builder.toString();
    }
}
