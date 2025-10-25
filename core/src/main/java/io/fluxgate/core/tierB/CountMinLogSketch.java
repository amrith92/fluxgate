package io.fluxgate.core.tierB;

import java.time.Duration;
import java.util.Arrays;
import java.util.Random;

/**
 * Probabilistic count-min sketch with small counters.
 */
public final class CountMinLogSketch {

    private final int depth;
    private final int width;
    private final long[][] counters;
    private final long[] epochs;
    private final long[] seeds;
    private final Duration sliceWindow;

    public CountMinLogSketch(int depth, int width, Duration sliceWindow) {
        this.depth = depth;
        this.width = width;
        this.sliceWindow = sliceWindow;
        this.counters = new long[depth][width];
        this.epochs = new long[width];
        this.seeds = new long[depth];
        Random random = new Random(42L);
        for (int i = 0; i < depth; i++) {
            seeds[i] = random.nextLong();
        }
    }

    public void increment(long key, long nowNanos) {
        for (int i = 0; i < depth; i++) {
            int index = indexFor(key, i);
            long epoch = epochs[index];
            long window = nowNanos / sliceWindow.toNanos();
            if (epoch != window) {
                counters[i][index] = 0;
                epochs[index] = window;
            }
            counters[i][index] = Math.min(Long.MAX_VALUE, counters[i][index] + 1);
        }
    }

    public long estimate(long key) {
        long min = Long.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
            int index = indexFor(key, i);
            min = Math.min(min, counters[i][index]);
        }
        return min == Long.MAX_VALUE ? 0 : min;
    }

    public void reset() {
        for (long[] row : counters) {
            Arrays.fill(row, 0);
        }
        Arrays.fill(epochs, 0);
    }

    int indexFor(long key, int depthIndex) {
        long hash = key ^ seeds[depthIndex];
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= (hash >>> 33);
        return (Math.floorMod(hash, width));
    }
}
