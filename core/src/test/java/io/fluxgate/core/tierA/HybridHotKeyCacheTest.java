package io.fluxgate.core.tierA;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HybridHotKeyCacheTest {

    @Test
    void constructorRejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new HybridHotKeyCache<>(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("capacity must be positive");
    }

    @Test
    void getIfPresentReturnsNullForMissingKey() {
        HybridHotKeyCache<Integer, String> cache = new HybridHotKeyCache<>(4);

        assertThat(cache.getIfPresent(99)).isNull();
        assertThat(cache.isHot(99)).isFalse();
        assertThat(cache.isProbationary(99)).isFalse();
    }

    @Test
    void getIfPresentPromotesProbationEntry() {
        HybridHotKeyCache<Integer, String> cache = new HybridHotKeyCache<>(6);

        cache.getOrCompute(1, () -> "one");

        String present = cache.getIfPresent(1);

        assertThat(present).isEqualTo("one");
        assertThat(cache.isHot(1)).isTrue();
        assertThat(cache.isProbationary(1)).isFalse();
    }

    @Test
    void getIfPresentDoesNotAdmitColdKeyWhenMainCacheIsFull() {
        HybridHotKeyCache<Integer, String> cache = new HybridHotKeyCache<>(4);

        cache.getOrCompute(1, () -> "one");
        cache.getOrCompute(1, () -> { throw new AssertionError("already cached"); });
        cache.getOrCompute(2, () -> "two");
        cache.getOrCompute(2, () -> { throw new AssertionError("already cached"); });

        for (int i = 0; i < 6; i++) {
            cache.getIfPresent(1);
            cache.getIfPresent(2);
        }

        cache.getOrCompute(99, () -> "newbie");
        assertThat(cache.isHot(99)).isFalse();
        assertThat(cache.isProbationary(99)).isTrue();

        String present = cache.getIfPresent(99);

        assertThat(present).isEqualTo("newbie");
        assertThat(cache.isHot(99)).isFalse();
        assertThat(cache.isProbationary(99)).isTrue();
        assertThat(cache.hotSize()).isEqualTo(2);
    }

    @Test
    void probationQueueEvictsOldestWhenCapacityExceeded() {
        HybridHotKeyCache<Integer, String> cache = new HybridHotKeyCache<>(3);

        cache.getOrCompute(1, () -> "one");
        cache.getOrCompute(2, () -> "two");
        cache.getOrCompute(3, () -> "three");

        assertThat(cache.getIfPresent(1)).isNull();
        assertThat(cache.isProbationary(1)).isFalse();
        assertThat(cache.isProbationary(2)).isTrue();
        assertThat(cache.isProbationary(3)).isTrue();
    }
}
