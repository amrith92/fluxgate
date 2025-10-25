package io.fluxgate.core.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
