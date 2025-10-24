package io.fluxgate.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.fluxgate.core.TierB.CountMinLogSketch;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public final class CountMinLogSketchTest {

    @Test
    void estimateIncreasesWithInserts() {
        CountMinLogSketch sketch = new CountMinLogSketch(4, 1024, Duration.ofSeconds(1));
        long key = 1234L;
        assertThat(sketch.estimate(key)).isZero();
        for (int i = 0; i < 100; i++) {
            sketch.increment(key, i);
        }
        assertThat(sketch.estimate(key)).isPositive();
    }
}
