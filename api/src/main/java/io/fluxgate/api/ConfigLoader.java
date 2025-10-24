package io.fluxgate.api;

import io.fluxgate.core.Policy.LimitPolicy;
import io.fluxgate.core.Policy.PolicyCompiler;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;

public final class ConfigLoader {

    private ConfigLoader() {}

    public static Collection<LimitPolicy> load(Path path) {
        return PolicyCompiler.fromYaml(path);
    }

    public static Collection<LimitPolicy> load(InputStream inputStream) {
        return PolicyCompiler.fromYaml(inputStream);
    }
}
