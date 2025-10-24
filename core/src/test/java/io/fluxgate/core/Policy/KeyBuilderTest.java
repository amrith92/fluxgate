package io.fluxgate.core.Policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeyBuilderTest {

    @Test
    void buildHashIncludesAttributes() {
        // Arrange
        KeyBuilder builder = KeyBuilder.of()
                .ip("127.0.0.1")
                .route("/")
                .header("auth", "token");

        // Act
        long hash = builder.buildHash("secret");

        // Assert
        assertThat(hash).isNotZero();
    }

    @Test
    void attributeSkipsNullValues() {
        // Arrange
        KeyBuilder builder = KeyBuilder.of().attribute("ip", null).route("/index");

        // Act
        long hash = builder.buildHash("secret");

        // Assert
        assertThat(hash).isNotZero();
    }
}
