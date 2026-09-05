package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * The manifest of a finished archive, read for the lines that decide how a runtime treats the rest of
 * the archive.
 *
 * <p>An archive carrying no manifest at all declares nothing, which is the same answer as an archive
 * whose manifest omits the line being asked about. Both are read here as an absent answer rather than
 * as a missing file, because what a rule acts on is the declaration and not the paperwork behind it.
 *
 * @param declared the manifest the archive carries, where it carries one
 */
record PackagedManifest(Optional<Manifest> declared) {

    private static final String MULTI_RELEASE = "Multi-Release";
    private static final String MAIN_CLASS = "Main-Class";
    private static final String NATIVE_ACCESS = "Enable-Native-Access";
    private static final String DECLARED = "true";

    /**
     * Reads the manifest of an open archive.
     *
     * @param archive archive to read
     * @return the manifest it carries, where it carries one
     * @throws IOException when the archive cannot be read
     */
    static PackagedManifest of(JarFile archive) throws IOException {
        return new PackagedManifest(Optional.ofNullable(archive.getManifest()));
    }

    /**
     * Whether a runtime reads the classes the archive ships under {@code META-INF/versions}.
     *
     * @return {@code true} when the archive declares itself multi-release
     */
    boolean multiRelease() {
        return this.attribute(MULTI_RELEASE).filter(DECLARED::equalsIgnoreCase).isPresent();
    }

    /**
     * Whether the archive is one a runtime starts on its own.
     *
     * @return {@code true} when the archive names a main class
     */
    boolean runnable() {
        return this.attribute(MAIN_CLASS).isPresent();
    }

    /**
     * Whether the archive states that its classes reach the operating system.
     *
     * @return {@code true} when the archive declares native access
     */
    boolean nativeAccess() {
        return this.attribute(NATIVE_ACCESS).isPresent();
    }

    private Optional<String> attribute(String name) {
        return this.declared()
            .map(Manifest::getMainAttributes)
            .map(values -> values.getValue(name));
    }
}
