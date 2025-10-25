package io.fluxgate.core.observability;

import io.fluxgate.core.tierB.HeavyKeeper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeatmapReporterTest {

    @Test
    void renderTopKeysFormatsEntries() {
        // Arrange
        HeavyKeeper heavyKeeper = new HeavyKeeper(4, 0.5d);
        heavyKeeper.offer(0x1aL);
        heavyKeeper.offer(0x1aL);
        heavyKeeper.offer(0x2bL);
        HeatmapReporter reporter = new HeatmapReporter(heavyKeeper);

        // Act
        String output = reporter.renderTopKeys();

        // Assert
        assertThat(output)
                .contains("1a:")
                .contains("2b:")
                .endsWith("\n");
    }
}
