package io.fluxgate.core.tierA;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HybridHotKeyCacheTest {

    @Test
    void promotesKeyAfterSecondAccess() {
        HybridHotKeyCache<Integer, String> cache = new HybridHotKeyCache<>(8);
        AtomicInteger factoryCalls = new AtomicInteger();

        String first = cache.getOrCompute(42, () -> {
            factoryCalls.incrementAndGet();
            return "value";
        });

        assertThat(first).isEqualTo("value");
        assertThat(factoryCalls.get()).isEqualTo(1);
        assertThat(cache.isHot(42)).isFalse();
        assertThat(cache.isProbationary(42)).isTrue();

        String second = cache.getOrCompute(42, () -> {
            throw new AssertionError("Supplier should not run on promotion");
        });

        assertThat(second).isEqualTo("value");
        assertThat(cache.isHot(42)).isTrue();
        assertThat(cache.isProbationary(42)).isFalse();
    }

    @Test
    void singleTouchStaysInProbation() {
        HybridHotKeyCache<Integer, String> cache = new HybridHotKeyCache<>(8);

        cache.getOrCompute(1, () -> "one");
        cache.getOrCompute(2, () -> "two");
        cache.getOrCompute(1, () -> {
            throw new AssertionError("already cached");
        });
        cache.getOrCompute(2, () -> {
            throw new AssertionError("already cached");
        });

        assertThat(cache.hotSize()).isEqualTo(2);

        cache.getOrCompute(99, () -> "burst");

        assertThat(cache.isHot(99)).isFalse();
        assertThat(cache.isProbationary(99)).isTrue();
        assertThat(cache.hotSize()).isEqualTo(2);
    }

    @Test
    void frequentKeyDisplacesStaleEntry() {
        HybridHotKeyCache<Integer, String> cache = new HybridHotKeyCache<>(6);

        cache.getOrCompute(1, () -> "one");
        cache.getOrCompute(1, () -> {
            throw new AssertionError("cached");
        });
        cache.getOrCompute(2, () -> "two");
        cache.getOrCompute(2, () -> {
            throw new AssertionError("cached");
        });
        cache.getOrCompute(3, () -> "three");
        cache.getOrCompute(3, () -> {
            throw new AssertionError("cached");
        });
        cache.getOrCompute(4, () -> "four");
        cache.getOrCompute(4, () -> {
            throw new AssertionError("cached");
        });

        assertThat(cache.isHot(1)).isTrue();
        assertThat(cache.isHot(2)).isTrue();
        assertThat(cache.isHot(3)).isTrue();
        assertThat(cache.isHot(4)).isTrue();

        cache.getOrCompute(2, () -> {
            throw new AssertionError("cached");
        });
        cache.getOrCompute(3, () -> {
            throw new AssertionError("cached");
        });
        cache.getOrCompute(4, () -> {
            throw new AssertionError("cached");
        });

        // Make key 1 stale by not touching it and hammer a new key until it promotes.
        for (int i = 0; i < 10; i++) {
            cache.getOrCompute(99, () -> "ninety-nine");
        }

        assertThat(cache.isHot(99)).isTrue();
        assertThat(cache.isHot(1)).isFalse();
    }
}
