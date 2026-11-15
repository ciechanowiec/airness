package eu.ciechanowiec.airness.maven;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One package OSV is asked about, and the artifact its findings are reported against.
 *
 * <p>A language package is asked by its package URL, which carries its version. A distribution's system package is
 * asked by its source package and its distribution's release instead: a distribution files its advisories under the
 * source package a binary package was built from, in an ecosystem of its own per release, and OSV matches no
 * advisory to a binary package's URL. The finding is still reported against the binary package's URL, which is what
 * the image holds.
 *
 * @param artifact  the package URL findings are reported against
 * @param ecosystem the OSV ecosystem of a system package, such as {@code Alpine:v3.24}, empty for a package URL
 * @param name      the source package of a system package, empty for a package URL
 * @param version   the source package's version, empty for a package URL
 */
record OsvQuery(String artifact, String ecosystem, String name, String version) {

    private static final String FIXED = "fixed";

    /**
     * Asks about a package by its package URL.
     *
     * @param purl the package URL, with its version
     * @return the query
     */
    static OsvQuery purl(String purl) {
        return new OsvQuery(purl, "", "", "");
    }

    /**
     * Asks about a system package by the source package it was built from.
     *
     * @param artifact  the binary package's URL
     * @param ecosystem the distribution release's OSV ecosystem
     * @param name      the source package
     * @param version   the source package's version as the distribution writes it
     * @return the query
     */
    static OsvQuery system(String artifact, String ecosystem, String name, String version) {
        return new OsvQuery(artifact, ecosystem, name, version);
    }

    /**
     * The body of this query in a batch.
     *
     * @param token the page token OSV returned for it, empty for its first page
     * @return the query as OSV takes it
     */
    Map<String, Object> body(String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (this.ecosystem.isEmpty()) {
            body.put("package", Map.of("purl", this.artifact));
        } else {
            body.put("package", Map.of("name", this.name, "ecosystem", this.ecosystem));
            body.put("version", this.version);
        }
        if (!token.isEmpty()) {
            body.put("page_token", token);
        }
        return Map.copyOf(body);
    }

    /**
     * Whether the advisory names a version that fixes the package asked about: for a system package, a fixed event
     * in a range of the distribution release's own entry for its source package, and for a package URL, one in any
     * range.
     *
     * @param vulnerability the advisory OSV returned
     * @return whether a fixed version exists
     */
    boolean fixedIn(JsonNode vulnerability) {
        return vulnerability.path("affected").valueStream().filter(this::asked)
            .flatMap(affected -> affected.path("ranges").valueStream())
            .flatMap(range -> range.path("events").valueStream())
            .anyMatch(event -> event.hasNonNull(FIXED));
    }

    private boolean asked(JsonNode affected) {
        if (this.ecosystem.isEmpty()) {
            return true;
        }
        JsonNode affectedPackage = affected.path("package");
        boolean release = this.ecosystem.equals(affectedPackage.path("ecosystem").asText());
        return release && this.name.equals(affectedPackage.path("name").asText());
    }
}
