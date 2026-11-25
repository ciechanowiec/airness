package eu.ciechanowiec.airness.governance;

import java.util.List;

/**
 * A package a container image the build ships holds, as the image's own metadata lists it.
 *
 * <p>The license policy judges the Maven and npm packages alone. A system package of an image, which the
 * distribution's package manager installed, carries the licenses its distribution declares, kept as written, and is
 * listed so a reader sees everything the image ships.
 *
 * @param image    the image, named by the Dockerfile that built it and the distribution it is built on
 * @param purl     the package URL
 * @param licenses the licenses the image's metadata declares, as written
 * @param system   whether a distribution's package manager installed the package
 */
public record ReportImagePackage(String image, String purl, List<String> licenses, boolean system) {

    public ReportImagePackage {
        licenses = List.copyOf(licenses);
    }
}
