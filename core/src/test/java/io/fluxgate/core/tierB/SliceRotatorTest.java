package io.fluxgate.core.tierB;

import java.lang.reflect.Field;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SliceRotatorTest {

    @Test
    void rotateIfNeededResetsSketchWhenThresholdReached() throws Exception {
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

        long[][] epochs = extractEpochs(sketch);
        for (long[] row : epochs) {
            assertThat(row).containsOnly(0L);
        }
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

    private static long[][] extractEpochs(CountMinLogSketch sketch) throws Exception {
        Field field = CountMinLogSketch.class.getDeclaredField("epochs");
        field.setAccessible(true);
        return (long[][]) field.get(sketch);
    }
}
