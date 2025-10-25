package io.fluxgate.core.TierA;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simple Count-Min sketch implementation for frequency estimation.
 */
final class FrequencySketch<K> {

    private final int depth;
    private final int widthMask;
    private final int[][] table;
    private final long[] seeds;

    FrequencySketch(int depth, int width) {
        if (Integer.bitCount(width) != 1) {
            throw new IllegalArgumentException("width must be a power of two");
        }
        this.depth = depth;
        this.widthMask = width - 1;
        this.table = new int[depth][width];
        this.seeds = new long[depth];
        for (int i = 0; i < depth; i++) {
            seeds[i] = ThreadLocalRandom.current().nextLong();
        }
    }

    void increment(K key) {
        long hash = spread(key);
        for (int i = 0; i < depth; i++) {
            int index = indexFor(hash, i);
            int[] row = table[i];
            if (row[index] != Integer.MAX_VALUE) {
                row[index]++;
            }
        }
    }

    int estimate(K key) {
        long hash = spread(key);
        int estimate = Integer.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
            int index = indexFor(hash, i);
            estimate = Math.min(estimate, table[i][index]);
        }
        return estimate == Integer.MAX_VALUE ? 0 : estimate;
    }

    private int indexFor(long hash, int row) {
        long seed = seeds[row];
        long mixed = hash ^ (seed + (seed >>> 33));
        mixed ^= (mixed >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= (mixed >>> 33);
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= (mixed >>> 33);
        return (int) mixed & widthMask;
    }

    private long spread(Object key) {
        long h = key == null ? 0L : key.hashCode();
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return h;
    }
}
