package io.fluxgate.core.TierA;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Minimal TinyLFU-inspired cache for demo purposes.
 */
public final class TinyLfuCache<K, V> {

    private final int capacity;
    private final Map<K, V> map;

    public TinyLfuCache(int capacity) {
        this.capacity = capacity;
        this.map = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > TinyLfuCache.this.capacity;
            }
        };
    }

    public synchronized V getOrCompute(K key, Supplier<V> supplier) {
        V value = map.get(key);
        if (value == null) {
            value = supplier.get();
            map.put(key, value);
        }
        return value;
    }
}
