package eu.ciechanowiec.airness.maven;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.eclipse.aether.DefaultSessionData;
import org.eclipse.aether.SessionData;

/**
 * A distinct copy of Airness, sharing only the resolver API and JDK with other plugin realms.
 */
final class PluginRealm implements AutoCloseable {

    private final URLClassLoader loader;

    PluginRealm(ClassLoader api) {
        URL[] entries = Stream.of(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(Path::of).map(PluginRealm::url).toArray(URL[]::new);
        this.loader = new URLClassLoader(entries, api);
    }

    static URLClassLoader api() {
        URL source = Objects.requireNonNull(SessionData.class.getProtectionDomain().getCodeSource()).getLocation();
        return new URLClassLoader(new URL[] {source}, ClassLoader.getPlatformClassLoader());
    }

    @SneakyThrows
    Object session() {
        return this.loader.loadClass(DefaultSessionData.class.getName()).getConstructor().newInstance();
    }

    @SneakyThrows
    Method method(String name, Class<?>... parameters) {
        Class<?>[] arguments = Stream.of(parameters).map(this::loaded).toArray(Class<?>[]::new);
        return this.loader.loadClass(RealmProbe.class.getName()).getMethod(name, arguments);
    }

    @SneakyThrows
    private Class<?> loaded(Class<?> type) {
        return this.loader.loadClass(type.getName());
    }

    @SneakyThrows
    private static URL url(Path path) {
        return path.toUri().toURL();
    }

    @Override
    public void close() throws IOException {
        this.loader.close();
    }
}
