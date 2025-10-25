package io.fluxgate.api;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @Test
    void constructorThrowsIllegalStateException() throws NoSuchMethodException {
        Constructor<ConfigLoader> ctor = ConfigLoader.class.getDeclaredConstructor();
        ctor.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, ctor::newInstance);
        assertInstanceOf(IllegalStateException.class, thrown.getTargetException());
        assertTrue(thrown.getTargetException().getMessage().contains("Cannot instantiate"));
    }

    @Test
    void loadWithNullPathPropagatesException() {
        assertThrows(Throwable.class, () -> ConfigLoader.load((Path) null));
    }

    @Test
    void loadWithNullInputStreamPropagatesException() {
        assertThrows(Throwable.class, () -> ConfigLoader.load((InputStream) null));
    }
}
