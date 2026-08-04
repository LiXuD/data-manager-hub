package com.dataplatform.plugin.spi;

import java.util.Arrays;
import java.util.Objects;

public final class SecretValue implements AutoCloseable {

    private char[] value;

    public SecretValue(char[] value) {
        this.value = Objects.requireNonNull(value, "value").clone();
    }

    public synchronized char[] copy() {
        ensureOpen();
        return value.clone();
    }

    public synchronized String materialize() {
        ensureOpen();
        return new String(value);
    }

    @Override
    public synchronized void close() {
        if (value != null) {
            Arrays.fill(value, '\0');
            value = null;
        }
    }

    private void ensureOpen() {
        if (value == null) {
            throw new IllegalStateException("secret has been cleared");
        }
    }
}
