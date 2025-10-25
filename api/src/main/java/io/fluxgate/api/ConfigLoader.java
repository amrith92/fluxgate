package io.fluxgate.api;

import io.fluxgate.core.policy.CompiledPolicySet;
import io.fluxgate.core.policy.PolicyCompiler;

import java.io.InputStream;
import java.nio.file.Path;

public final class ConfigLoader {

    private ConfigLoader() {
        throw new IllegalStateException("Cannot instantiate " + getClass());
    }

    public static CompiledPolicySet load(Path path) {
        return PolicyCompiler.fromYaml(path);
    }

    public static CompiledPolicySet load(InputStream inputStream) {
        return PolicyCompiler.fromYaml(inputStream);
    }
}
