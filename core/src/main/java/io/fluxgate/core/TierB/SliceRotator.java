package io.fluxgate.core.TierB;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Maintains sketch rotation over time windows.
 */
public final class SliceRotator {

    private final CountMinLogSketch sketch;
    private final Duration rotationPeriod;
    private final AtomicLong nextRotation = new AtomicLong();

    public SliceRotator(CountMinLogSketch sketch, Duration rotationPeriod) {
        this.sketch = sketch;
        this.rotationPeriod = rotationPeriod;
    }

    public void rotateIfNeeded(long nowNanos) {
        long threshold = nextRotation.get();
        if (nowNanos < threshold) {
            return;
        }
        long next = nowNanos + rotationPeriod.toNanos();
        if (nextRotation.compareAndSet(threshold, next)) {
            sketch.reset();
        }
    }
}
