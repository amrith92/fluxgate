package io.fluxgate.core.tierA;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Hybrid admission and eviction policy combining probationary filtering with a TinyLFU-style sketch.
 */
public final class HybridHotKeyCache<K, V> {

    private static final int DEFAULT_DEPTH = 4;
    private static final int DEFAULT_WIDTH = 1 << 12;
    private static final int SAMPLE_SIZE = 16;
    private final int capacity;
    private final int probationCapacity;
    private final int mainCapacity;
    private final LinkedHashMap<K, CacheEntry<V>> mainCache;
    private final LinkedHashMap<K, CacheEntry<V>> probationQueue;
    private final FrequencySketch<K> frequencySketch;
    private long accessCounter;

    public HybridHotKeyCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.probationCapacity = Math.max(1, capacity / 8);
        this.mainCapacity = Math.max(1, capacity - probationCapacity);
        this.mainCache = new LinkedHashMap<>(capacity, 0.75f, true);
        this.probationQueue = new LinkedHashMap<>(probationCapacity, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                return size() > HybridHotKeyCache.this.probationCapacity;
            }
        };
        this.frequencySketch = new FrequencySketch<>(DEFAULT_DEPTH, DEFAULT_WIDTH);
    }

    public synchronized V getOrCompute(K key, Supplier<V> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        accessCounter++;
        long now = accessCounter;
        frequencySketch.increment(key);

        CacheEntry<V> entry = mainCache.get(key);
        if (entry != null) {
            entry.touch(now);
            return entry.value;
        }

        CacheEntry<V> probationEntry = probationQueue.remove(key);
        if (probationEntry != null) {
            probationEntry.touch(now);
            admit(key, probationEntry, now);
            return probationEntry.value;
        }

        CacheEntry<V> newEntry = new CacheEntry<>(supplier.get(), now);
        probationQueue.put(key, newEntry);
        considerAdmission(key, newEntry, now);
        return newEntry.value;
    }

    private void considerAdmission(K key, CacheEntry<V> entry, long now) {
        Map.Entry<K, CacheEntry<V>> victim = selectVictim(now);
        if (victim == null) {
            if (frequencySketch.estimate(key) > 1) {
                probationQueue.remove(key);
                admit(key, entry, now);
            }
            return;
        }

        long candidateScore = score(key, entry, now);
        long victimScore = score(victim.getKey(), victim.getValue(), now);
        if (candidateScore > victimScore) {
            mainCache.remove(victim.getKey());
            probationQueue.remove(key);
            admit(key, entry, now);
        }
    }

    private void admit(K key, CacheEntry<V> entry, long now) {
        entry.lastAccessTick = now;
        mainCache.put(key, entry);
        trimIfNeeded(now);
    }

    private void trimIfNeeded(long now) {
        while (mainCache.size() > mainCapacity) {
            Map.Entry<K, CacheEntry<V>> victim = selectVictim(now);
            if (victim == null) {
                Iterator<Map.Entry<K, CacheEntry<V>>> iterator = mainCache.entrySet().iterator();
                if (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
                return;
            }
            mainCache.remove(victim.getKey());
        }
    }

    private Map.Entry<K, CacheEntry<V>> selectVictim(long now) {
        Map.Entry<K, CacheEntry<V>> worst = null;
        int sampled = 0;
        for (Map.Entry<K, CacheEntry<V>> entry : mainCache.entrySet()) {
            if (sampled++ >= SAMPLE_SIZE) {
                break;
            }
            if (worst == null) {
                worst = entry;
                continue;
            }
            long currentScore = score(entry.getKey(), entry.getValue(), now);
            long worstScore = score(worst.getKey(), worst.getValue(), now);
            if (currentScore < worstScore) {
                worst = entry;
            }
        }
        return worst;
    }

    private long score(K key, CacheEntry<V> entry, long now) {
        int freq = frequencySketch.estimate(key);
        long recencyPenalty = now - entry.lastAccessTick;
        long residencyPenalty = now - entry.admissionTick;
        return (long) freq * 100L - recencyPenalty - residencyPenalty;
    }

    public synchronized boolean isHot(K key) {
        return mainCache.containsKey(key);
    }

    public synchronized boolean isProbationary(K key) {
        return probationQueue.containsKey(key);
    }

    public synchronized int hotSize() {
        return mainCache.size();
    }

    public int getCapacity() {
        return capacity;
    }

    private static final class CacheEntry<V> {
        private final V value;
        private final long admissionTick;
        private long lastAccessTick;

        private CacheEntry(V value, long admissionTick) {
            this.value = value;
            this.admissionTick = admissionTick;
            this.lastAccessTick = admissionTick;
        }

        private void touch(long accessTick) {
            this.lastAccessTick = accessTick;
        }
    }
}
