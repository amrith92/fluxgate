package io.fluxgate.core.Observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.fluxgate.core.TierB.HeavyKeeper;
import org.junit.jupiter.api.Test;

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
        assertThat(output).contains("1a:");
        assertThat(output).contains("2b:");
        assertThat(output).endsWith("\n");
    }
}
