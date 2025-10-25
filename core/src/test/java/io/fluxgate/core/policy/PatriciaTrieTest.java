package io.fluxgate.core.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PatriciaTrieTest {

    @Test
    void matchesCidrsAndIps() {
        PatriciaTrie trie = new PatriciaTrie();
        trie.insert("10.0.0.0/8");
        trie.insert("10.1.2.0/24");
        trie.insert("192.168.1.10");
        trie.freeze();

        assertThat(trie.matches("10.0.1.5")).isTrue();
        assertThat(trie.matches("10.1.2.9")).isTrue();
        assertThat(trie.matches("192.168.1.10")).isTrue();
        assertThat(trie.matches("192.168.1.11")).isFalse();
        assertThat(trie.matches("11.0.0.1")).isFalse();
    }

    @Test
    void requiresFreezeBeforeLookup() {
        PatriciaTrie trie = new PatriciaTrie();
        trie.insert("10.0.0.0/8");
        assertThatThrownBy(() -> trie.matches("10.1.1.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Trie must be frozen");
    }

    @Test
    void compressesPrefixes() {
        PatriciaTrie trie = new PatriciaTrie();
        trie.insert("10.0.0.0/8");
        trie.insert("10.128.0.0/9");
        trie.insert("10.255.255.255");
        trie.freeze();

        assertThat(trie.matches("10.200.0.1")).isTrue();
        assertThat(trie.matches("10.127.255.255")).isTrue();
        assertThat(trie.matches("11.0.0.0")).isFalse();
    }
}
