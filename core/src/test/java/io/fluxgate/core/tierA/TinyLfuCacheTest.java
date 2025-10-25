package io.fluxgate.core.tierA;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TinyLfuCacheTest {

    @Test
    void getOrComputeCachesValueAndReusesSupplier() {
        // Arrange
        TinyLfuCache<String, Integer> cache = new TinyLfuCache<>(2);
        AtomicInteger creations = new AtomicInteger();

        // Act
        Integer first = cache.getOrCompute("a", creations::incrementAndGet);
        Integer second = cache.getOrCompute("a", creations::incrementAndGet);

        // Assert
        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
        assertThat(creations.get()).isEqualTo(1);
    }

    @Test
    void getOrComputeEvictsLeastRecentlyUsedEntry() {
        // Arrange
        TinyLfuCache<String, Integer> cache = new TinyLfuCache<>(2);
        AtomicInteger aCreations = new AtomicInteger();
        AtomicInteger bCreations = new AtomicInteger();

        // Act
        cache.getOrCompute("a", aCreations::incrementAndGet);
        cache.getOrCompute("b", bCreations::incrementAndGet);
        cache.getOrCompute("a", aCreations::incrementAndGet);
        cache.getOrCompute("c", () -> 3);
        cache.getOrCompute("b", bCreations::incrementAndGet);

        // Assert
        assertThat(aCreations.get()).isEqualTo(1);
        assertThat(bCreations.get()).isEqualTo(2);
    }
}
