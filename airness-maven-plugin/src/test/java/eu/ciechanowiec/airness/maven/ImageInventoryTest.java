package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * What an image holds reads as its distribution's release and packages, a system package by the source package its
 * distribution files advisories under, and each is asked of OSV the way OSV reads it.
 */
class ImageInventoryTest {

    private static final String DOCKERFILE = "Dockerfile";

    @Test
    void readsASystemPackageByItsSourcePackageAndWithoutItsArchitecture() {
        ImagePackage held = ImageInventory.of(
            ImageDocuments.document("alpine", "3.24.1", ImageDocuments.apk("libcrypto3", "openssl", "3.5.7-r0"))
        ).packages().getFirst();
        assertAll(
            () -> assertEquals("pkg:apk/alpine/libcrypto3@3.5.7-r0?distro=3.24.1", held.purl()),
            () -> assertTrue(held.system()),
            () -> assertEquals("openssl", held.source()),
            () -> assertEquals("3.5.7-r0", held.sourceVersion()),
            () -> assertEquals("system:openssl", held.identity()),
            () -> assertEquals(List.of("Zlib"), held.licenses())
        );
    }

    @Test
    void writesADebianSourceVersionWithItsEpochAndRevision() {
        String component = """
            {"name": "bsdutils", "version": "1:2.38.1-5+b1",
             "purl": "pkg:deb/debian/bsdutils@2.38.1-5%2Bb1?arch=arm64&distro=debian-12.4&epoch=1",
             "properties": [{"name": "aquasecurity:trivy:SrcName", "value": "util-linux"},
                            {"name": "aquasecurity:trivy:SrcEpoch", "value": "2"},
                            {"name": "aquasecurity:trivy:SrcVersion", "value": "2.38.1"},
                            {"name": "aquasecurity:trivy:SrcRelease", "value": "5"}]}
            """;
        String unepoched = """
            {"name": "libc6", "version": "2.36-9", "purl": "pkg:deb/debian/libc6@2.36-9?distro=debian-12.4&arch=arm64",
             "properties": [{"name": "aquasecurity:trivy:SrcName", "value": "glibc"},
                            {"name": "aquasecurity:trivy:SrcEpoch", "value": "0"},
                            {"name": "aquasecurity:trivy:SrcVersion", "value": "2.36"}]}
            """;
        List<ImagePackage> packages = ImageInventory.of(ImageDocuments.document("debian", "12.4", component, unepoched))
            .packages();
        assertAll(
            () -> assertEquals("2:2.38.1-5", packages.getFirst().sourceVersion()),
            () -> assertEquals(
                "pkg:deb/debian/bsdutils@2.38.1-5%2Bb1?distro=debian-12.4&epoch=1", packages.getFirst().purl()
            ),
            () -> assertEquals("2.36", packages.getLast().sourceVersion()),
            () -> assertEquals("pkg:deb/debian/libc6@2.36-9?distro=debian-12.4", packages.getLast().purl())
        );
    }

    @Test
    void readsALanguagePackageByItsPackageUrl() {
        ImageInventory inventory = ImageInventory.of(
            ImageDocuments.document("alpine", "3.24.1", ImageDocuments.npm("undici", "6.28.0"))
        );
        ImagePackage held = inventory.packages().getFirst();
        assertAll(
            () -> assertFalse(held.system()),
            () -> assertEquals("undici", held.source()),
            () -> assertEquals("6.28.0", held.sourceVersion()),
            () -> assertEquals("pkg:npm/undici", held.identity()),
            () -> assertEquals(List.of("MIT"), held.licenses())
        );
    }

    @Test
    void namesTheEcosystemOfEachDistributionRelease() {
        assertAll(
            () -> assertEquals(
                "Alpine:v3.24", ImageInventory.of(ImageDocuments.document("alpine", "3.24.1")).ecosystem(DOCKERFILE)
            ),
            () -> assertEquals(
                "Debian:13", ImageInventory.of(ImageDocuments.document("debian", "13.7")).ecosystem(DOCKERFILE)
            ),
            () -> assertEquals(
                "Ubuntu:24.04:LTS", ImageInventory.of(ImageDocuments.document("ubuntu", "24.04")).ecosystem(DOCKERFILE)
            ),
            () -> assertEquals(
                "Ubuntu:25.10", ImageInventory.of(ImageDocuments.document("ubuntu", "25.10")).ecosystem(DOCKERFILE)
            ),
            () -> assertEquals(
                "Ubuntu:23.04", ImageInventory.of(ImageDocuments.document("ubuntu", "23.04")).ecosystem(DOCKERFILE)
            ),
            () -> assertEquals(
                "Alpine", ImageInventory.of(ImageDocuments.document("alpine", "3.24.1")).distributionName()
            )
        );
    }

    @Test
    void refusesSystemPackagesOfADistributionItCannotLookUp() {
        ImageInventory inventory = ImageInventory.of(
            ImageDocuments.document("wolfi", "20230201", ImageDocuments.apk("glibc", "glibc", "2.42-r1"))
        );
        IllegalStateException refusal = assertThrows(IllegalStateException.class, () -> inventory.queries(DOCKERFILE));
        assertAll(
            () -> assertTrue(
                Objects.requireNonNull(refusal.getMessage()).contains("Dockerfile builds an image on wolfi 20230201")
            ),
            () -> assertTrue(
                Objects.requireNonNull(refusal.getMessage()).contains("build it on an Alpine, Debian or Ubuntu base")
            )
        );
    }

    @Test
    void asksEachSystemPackageBySourceAndEachLanguagePackageOsvReadsByUrl() {
        String jar = """
            {"name": "jackson-databind", "version": "2.21.0", "purl": "pkg:maven/com.fasterxml/jackson-databind@2.21.0"}
            """;
        String system = "{\"type\": \"operating-system\", \"name\": \"alpine\", \"version\": \"3.24.1\"}";
        ImageInventory inventory = ImageInventory.of(
            ImageDocuments.document(
                "alpine", "3.24.1", ImageDocuments.apk("zlib", "zlib", "1.3.2-r0"), ImageDocuments.npm(
                    "glob", "13.0.6"
                ), jar,
                system
            )
        );
        ImageInventory bare = ImageInventory.of(ImageDocuments.document("", "", ImageDocuments.npm("a", "1.0.0")));
        assertAll(
            () -> assertEquals(
                List.of(
                    OsvQuery.system("pkg:apk/alpine/zlib@1.3.2-r0?distro=3.24.1", "Alpine:v3.24", "zlib", "1.3.2-r0"),
                    OsvQuery.purl("pkg:npm/glob@13.0.6")
                ),
                inventory.queries(DOCKERFILE)
            ),
            () -> assertEquals(Optional.empty(), inventory.held("pkg:npm/glob@1.0.0")),
            () -> assertEquals(
                List.of(OsvQuery.purl("pkg:npm/a@1.0.0")), bare.queries(DOCKERFILE),
                "an image without system packages needs no distribution"
            )
        );
    }

    @Test
    void readsWhatAComponentLeavesOutAsThePackageItself() {
        String bare = """
            {"name": "musl", "version": "1.2.6-r2", "purl": "pkg:apk/alpine/musl@1.2.6-r2?arch=x86_64",
             "licenses": [{"license": {"name": ""}}]}
            """;
        String unversioned = "{\"name\": \"tool\", \"purl\": \"pkg:golang/example.com/tool\"}";
        List<ImagePackage> packages = ImageInventory.of(ImageDocuments.document("alpine", "3.24.1", bare, unversioned))
            .packages();
        assertAll(
            () -> assertEquals(
                "pkg:apk/alpine/musl@1.2.6-r2", packages.getFirst().purl(), "an architecture alone leaves no query"
            ),
            () -> assertEquals("musl", packages.getFirst().source()),
            () -> assertEquals("1.2.6-r2", packages.getFirst().sourceVersion()),
            () -> assertEquals(List.of(), packages.getFirst().licenses(), "a license with no name names none"),
            () -> assertEquals("pkg:golang/example.com/tool", packages.getLast().identity())
        );
    }
}
