package io.fluxgate.core.policy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

public final class KeyBuilder {

    private final Map<String, String> attributes = new LinkedHashMap<>();

    private KeyBuilder() {
        // no-op
    }

    public static KeyBuilder of() {
        return new KeyBuilder();
    }

    public KeyBuilder attribute(String name, String value) {
        if (value != null) {
            attributes.put(name, value);
        }
        return this;
    }

    public KeyBuilder ip(String ip) {
        return attribute("ip", ip);
    }

    public KeyBuilder route(String route) {
        return attribute("route", route);
    }

    public KeyBuilder header(String name, String value) {
        return attribute("header:" + name, value);
    }

    public long buildHash(String secret) {
        StringBuilder builder = new StringBuilder();
        attributes.forEach((k, v) -> builder.append(k).append('=').append(v).append('\n'));
        return hash(builder.toString(), secret);
    }

    private long hash(String value, String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(secret.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            long result = 0L;
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                result = (result << 8) | (hash[i] & 0xff);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash key", e);
        }
    }
}
