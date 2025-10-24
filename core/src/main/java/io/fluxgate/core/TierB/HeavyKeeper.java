package io.fluxgate.core.TierB;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Simplified HeavyKeeper implementation capturing frequent keys.
 */
public final class HeavyKeeper {

    private final Entry[] entries;
    private final double decay;

    public HeavyKeeper(int capacity, double decay) {
        this.entries = new Entry[Math.max(1, capacity)];
        this.decay = decay;
    }

    public void offer(long key) {
        int index = (int) (Math.floorMod(Long.hashCode(key), entries.length));
        Entry entry = entries[index];
        if (entry == null) {
            entries[index] = new Entry(key, 1);
            return;
        }
        if (entry.key == key) {
            entry.count++;
            return;
        }
        entry.count = Math.max(1, (int) (entry.count * decay));
        if (entry.count == 1) {
            entry.key = key;
        }
    }

    public Entry[] topK() {
        PriorityQueue<Entry> queue = new PriorityQueue<>(Comparator.comparingInt(e -> -e.count));
        Arrays.stream(entries).filter(e -> e != null && e.count > 0).forEach(queue::offer);
        return queue.toArray(new Entry[0]);
    }

    public static final class Entry {
        long key;
        int count;

        public Entry(long key, int count) {
            this.key = key;
            this.count = count;
        }

        public long key() {
            return key;
        }

        public int count() {
            return count;
        }
    }
}
