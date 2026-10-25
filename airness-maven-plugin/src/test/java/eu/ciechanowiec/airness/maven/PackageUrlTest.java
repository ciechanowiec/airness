package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A report package outside Maven is named by its package URL, which becomes its SBOM component.
 */
class PackageUrlTest {

    private static final int SHA256_LENGTH = 64;

    @Test
    void readsAScopedNpmPackage() {
        PackageUrl url = PackageUrl.parse("pkg:npm/%40scope/kit@2.0.0");
        assertEquals("@scope", url.namespace());
        assertEquals("kit", url.name());
        assertEquals("2.0.0", url.version());
        Map<String, Object> component = url.component(List.of(), List.of());
        assertEquals("@scope", component.get("group"));
        assertEquals("pkg:npm/%40scope/kit@2.0.0", component.get("bom-ref"));
        assertFalse(component.containsKey("hashes"));
    }

    @Test
    void carriesTheChecksumOfAPinnedToolAsItsHash() {
        String hash = "a".repeat(SHA256_LENGTH);
        PackageUrl url = PackageUrl.parse("pkg:generic/node@26.11.1?checksum=sha256:" + hash);
        assertEquals("", url.namespace());
        assertEquals(hash, url.sha256());
        assertEquals(
            List.of(Map.of("alg", "SHA-256", "content", hash)), url.component(List.of(), List.of()).get("hashes")
        );
    }

    @Test
    void readsAPackageWithoutAVersionAndRefusesWhatIsNoPackageUrl() {
        assertEquals("", PackageUrl.parse("pkg:npm/private-app").version());
        assertFalse(PackageUrl.parse("pkg:npm/private-app").component(List.of(), List.of()).containsKey("version"));
        assertThrows(IllegalArgumentException.class, () -> PackageUrl.parse("com.example:library:1.0"));
    }
}
