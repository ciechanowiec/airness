package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Every manifest row the assets artifact ships has a body, and every body it ships has a row.
 *
 * <p>A row without a body reads as permanent drift for a pinned file and as a failure for a block, and a
 * body without a row is a file nobody receives. The self-build only places the generic and Java root
 * files, so the node rows would otherwise first meet a body lookup in a consumer.
 */
class ShippedAssetsTest {

    private static final Path RESOURCES = SelfModules.repository().resolve("airness-assets/src/main/resources");
    private static final Set<AssetPolicy> BODILESS = Set.of(AssetPolicy.FORBIDDEN, AssetPolicy.REFERENCE);

    @Test
    void shipsABodyForEveryRowAndARowForEveryBody() {
        AssetCatalogue catalogue = catalogue();
        List<ManagedAsset> rows = catalogue.assets().stream().filter(asset -> !BODILESS.contains(asset.policy()))
            .toList();
        assertTrue(rows.size() > 1, "a check that read nothing proves nothing about the shipped manifests");
        assertEquals(
            List.of(),
            rows.stream().filter(asset -> catalogue.canonical(asset).isEmpty()).toList(),
            "a row without a body is drift no project can repair"
        );
        Set<String> expected = rows.stream().map(ShippedAssetsTest::body).collect(Collectors.toSet());
        assertEquals(
            List.of(),
            Stream.concat(bodies("airness/files"), bodies("airness/project-files"))
                .filter(body -> !expected.contains(body))
                .toList(),
            "a body without a row is a file no project receives"
        );
    }

    private static String body(ManagedAsset asset) {
        String base = asset.location() == AssetLocation.ROOT ? "airness/files/" : "airness/project-files/";
        return base + asset.owner().id() + '/' + asset.path() + ".asset";
    }

    @SneakyThrows
    private static Stream<String> bodies(String base) {
        try (Stream<Path> files = Files.walk(RESOURCES.resolve(base))) {
            return files.filter(Files::isRegularFile)
                .map(file -> RESOURCES.relativize(file).toString().replace('\\', '/'))
                .toList()
                .stream();
        }
    }

    @SneakyThrows
    private static AssetCatalogue catalogue() {
        URL[] classpath = {RESOURCES.toUri().toURL()};
        return new AssetCatalogue(new URLClassLoader(classpath, null));
    }
}
