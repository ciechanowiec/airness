package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import org.eclipse.aether.SessionData;
import org.junit.jupiter.api.Test;

/**
 * Stack-layer plugin dependencies must not split the reactor's closing and once-only state.
 */
class SessionRealmsTest {

    private static final int WORKERS = 8;
    private static final int CALLS = 64;

    @Test
    @SneakyThrows
    void closesOnceWhenModulesRunInDifferentPluginRealms() {
        try (
            URLClassLoader api = PluginRealm.api();
            PluginRealm first = new PluginRealm(api);
            PluginRealm second = new PluginRealm(api)
        ) {
            Method plain = first.method(
                "complete", SessionData.class, String.class, Collection.class
            );
            Method layer = second.method(
                "complete", SessionData.class, String.class, Collection.class
            );
            assertNotSame(plain.getDeclaringClass(), layer.getDeclaringClass());
            Object data = first.session();
            Set<String> awaited = Set.of("plain", "layer");
            assertFalse((Boolean) plain.invoke(null, data, "plain", awaited));
            assertTrue((Boolean) layer.invoke(null, data, "layer", awaited));
            assertFalse((Boolean) plain.invoke(null, data, "plain", awaited));
            assertFalse((Boolean) layer.invoke(null, data, "layer", awaited));
        }
    }

    @Test
    @SneakyThrows
    void sharesOneClaimAcrossConcurrentPluginRealms() {
        try (
            URLClassLoader api = PluginRealm.api();
            PluginRealm first = new PluginRealm(api);
            PluginRealm second = new PluginRealm(api);
            ExecutorService executor = Executors.newFixedThreadPool(WORKERS)
        ) {
            List<Method> methods = List.of(
                first.method("firstRun", SessionData.class, Class.class, String.class),
                second.method("firstRun", SessionData.class, Class.class, String.class)
            );
            warm(methods, first);
            Object data = first.session();
            List<Callable<Object>> calls = IntStream.range(0, CALLS).mapToObj(
                index -> (Callable<Object>) () -> methods.get(index % methods.size())
                    .invoke(null, data, SessionRealmsTest.class, "shared")
            ).toList();
            List<Future<Object>> results = executor.invokeAll(calls);
            assertEquals(1L, results.stream().filter(SessionRealmsTest::claimed).count());
        }
    }

    @SneakyThrows
    private static void warm(List<Method> methods, PluginRealm realm) {
        for (Method method : methods) {
            assertTrue((Boolean) method.invoke(null, realm.session(), SessionRealmsTest.class, "warm"));
        }
    }

    @SneakyThrows
    private static boolean claimed(Future<Object> result) {
        return Boolean.TRUE.equals(result.get());
    }
}
