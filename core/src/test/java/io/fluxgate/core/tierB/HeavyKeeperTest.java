package io.fluxgate.core.tierB;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class HeavyKeeperTest {

    @Test
    void offerTracksFrequentKeys() {
        // Arrange
        HeavyKeeper heavyKeeper = new HeavyKeeper(8, 0.5d);
        long hotKey = 99L;

        // Act
        for (int i = 0; i < 10; i++) {
            heavyKeeper.offer(hotKey);
        }
        heavyKeeper.offer(100L);
        HeavyKeeper.Entry[] entries = heavyKeeper.topK();

        // Assert
        assertThat(entries).isNotEmpty();
        assertThat(Arrays.stream(entries).map(HeavyKeeper.Entry::key)).contains(hotKey);
        int maxCount = Arrays.stream(entries).mapToInt(HeavyKeeper.Entry::count).max().orElse(0);
        assertThat(maxCount).isGreaterThanOrEqualTo(10);
    }

    @Test
    void offerAppliesDecayWhenReplacingKeys() {
        // Arrange
        HeavyKeeper heavyKeeper = new HeavyKeeper(2, 0.5d);

        // Act
        heavyKeeper.offer(1L);
        heavyKeeper.offer(2L);
        heavyKeeper.offer(3L);
        HeavyKeeper.Entry[] entries = heavyKeeper.topK();

        // Assert
        assertThat(entries).hasSizeGreaterThanOrEqualTo(1);
        assertThat(entries[0].count()).isPositive();
    }
}
