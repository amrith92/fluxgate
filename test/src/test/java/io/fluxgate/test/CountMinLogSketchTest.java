package io.fluxgate.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.fluxgate.core.TierB.CountMinLogSketch;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public final class CountMinLogSketchTest {

    @Test
    void estimateIncreasesWithInserts() {
        // Arrange
        CountMinLogSketch sketch = new CountMinLogSketch(4, 1024, Duration.ofSeconds(1));
        long key = 1234L;

        // Act
        long before = sketch.estimate(key);
        for (int i = 0; i < 100; i++) {
            sketch.increment(key, i);
        }
        long after = sketch.estimate(key);

        // Assert
        assertThat(before).isZero();
        assertThat(after).isGreaterThan(before);
    }
}
