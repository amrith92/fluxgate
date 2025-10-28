package io.fluxgate.core.tierB;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CountMinLogSketchTest {

    @Test
    void incrementUpdatesEstimateForKey() {
        // Arrange
        CountMinLogSketch sketch = new CountMinLogSketch(4, 128, Duration.ofMillis(10));
        long key = 42L;

        // Act
        sketch.increment(key, 0L);
        sketch.increment(key, 0L);
        long estimate = sketch.estimate(key);

        // Assert
        assertThat(estimate).isEqualTo(2L);
    }

    @Test
    void resetClearsAllCounters() throws Exception {
        // Arrange
        CountMinLogSketch sketch = new CountMinLogSketch(4, 128, Duration.ofMillis(10));
        long key = 7L;

        // Act
        sketch.increment(key, 0L);
        sketch.reset();
        long estimate = sketch.estimate(key);

        // Assert
        assertThat(estimate).isZero();

        long[][] epochs = extractEpochs(sketch);
        for (long[] row : epochs) {
            assertThat(row).containsOnly(0L);
        }
    }

    @Test
    void incrementRollsOverWhenWindowChanges() {
        // Arrange
        Duration window = Duration.ofMillis(10);
        CountMinLogSketch sketch = new CountMinLogSketch(4, 128, window);
        long key = 15L;

        // Act
        sketch.increment(key, 0L);
        sketch.increment(key, window.toNanos() * 2);
        long estimate = sketch.estimate(key);

        // Assert
        assertThat(estimate).isEqualTo(1L);
    }

    @Test
    void countersResetIndependentlyPerRowWhenWindowAdvances() throws Exception {
        // Arrange
        Duration window = Duration.ofMillis(10);
        CountMinLogSketch sketch = new CountMinLogSketch(2, 1, window);
        long key = 21L;

        // Act
        sketch.increment(key, 0L);
        sketch.increment(key, window.toNanos());

        // Assert
        long[][] counters = extractCounters(sketch);
        assertThat(counters[0][0]).isEqualTo(1L);
        assertThat(counters[1][0]).isEqualTo(1L);
    }

    private static long[][] extractCounters(CountMinLogSketch sketch) throws Exception {
        Field field = CountMinLogSketch.class.getDeclaredField("counters");
        field.setAccessible(true);
        return (long[][]) field.get(sketch);
    }

    private static long[][] extractEpochs(CountMinLogSketch sketch) throws Exception {
        Field field = CountMinLogSketch.class.getDeclaredField("epochs");
        field.setAccessible(true);
        return (long[][]) field.get(sketch);
    }
}
