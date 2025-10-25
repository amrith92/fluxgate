package io.fluxgate.core.tierB;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Maintains sketch rotation over time windows.
 */
public final class SliceRotator {

    private final CountMinLogSketch sketch;
    private final long rotationPeriodNanos;
    private final AtomicLong nextRotation = new AtomicLong();

    public SliceRotator(CountMinLogSketch sketch, Duration rotationPeriod) {
        this.sketch = sketch;
        this.rotationPeriodNanos = rotationPeriod.toNanos();
        this.nextRotation.set(this.rotationPeriodNanos);
    }

    public void rotateIfNeeded(long nowNanos) {
        long threshold = nextRotation.get();
        if (nowNanos < threshold) {
            return;
        }
        long next = nowNanos + rotationPeriodNanos;
        if (nextRotation.compareAndSet(threshold, next)) {
            sketch.reset();
        }
    }
}
