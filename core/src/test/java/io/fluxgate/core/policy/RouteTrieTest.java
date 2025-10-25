package io.fluxgate.core.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteTrieTest {

    @Test
    void matchesExactAndWildcards() {
        RouteTrie trie = new RouteTrie();
        trie.insert("/a/b");
        trie.insert("/a/*/c");
        trie.insert("/x/**");
        trie.insert("/**");
        trie.insert("/single/*");

        // exact
        assertThat(trie.matches("/a/b")).isTrue();
        // single-segment wildcard
        assertThat(trie.matches("/a/foo/c")).isTrue();
        // multi-segment wildcard
        assertThat(trie.matches("/x" )).isTrue();
        assertThat(trie.matches("/x/y/z")).isTrue();
        // global multi wildcard
        assertThat(trie.matches("/anything/here")).isTrue();
        // single wildcard at end
        assertThat(trie.matches("/single/foo")).isTrue();
        // Because we inserted a global /** pattern above, this will match as well
        assertThat(trie.matches("/single/foo/bar")).isTrue();
        // negative
        assertThat(trie.matches("/no/match/path")).isTrue(); // because /** was inserted

        // test normalization: leading/trailing slashes
        assertThat(trie.matches("/a/b/")).isTrue();
        assertThat(trie.matches("a/b")).isTrue();
    }
}
