package io.fluxgate.core.tierB;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SliceRotatorTest {

    @Test
    void rotateIfNeededResetsSketchWhenThresholdReached() {
        // Arrange
        Duration rotation = Duration.ofMillis(5);
        CountMinLogSketch sketch = new CountMinLogSketch(2, 16, rotation);
        SliceRotator rotator = new SliceRotator(sketch, rotation);
        long key = 11L;
        sketch.increment(key, 0L);

        // Act
        rotator.rotateIfNeeded(rotation.toNanos());
        long estimate = sketch.estimate(key);

        // Assert
        assertThat(estimate).isZero();
    }

    @Test
    void rotateIfNeededIsNoOpBeforeThreshold() {
        // Arrange
        Duration rotation = Duration.ofMillis(5);
        CountMinLogSketch sketch = new CountMinLogSketch(2, 16, rotation);
        SliceRotator rotator = new SliceRotator(sketch, rotation);
        long key = 17L;
        sketch.increment(key, 0L);

        // Act
        rotator.rotateIfNeeded(rotation.toNanos() - 1);
        long estimate = sketch.estimate(key);

        // Assert
        assertThat(estimate).isOne();
    }
}
