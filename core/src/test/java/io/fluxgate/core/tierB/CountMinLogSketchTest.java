package io.fluxgate.core.tierB;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

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
    void resetClearsAllCounters() {
        // Arrange
        CountMinLogSketch sketch = new CountMinLogSketch(4, 128, Duration.ofMillis(10));
        long key = 7L;

        // Act
        sketch.increment(key, 0L);
        sketch.reset();
        long estimate = sketch.estimate(key);

        // Assert
        assertThat(estimate).isZero();
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
}
