package eu.ciechanowiec.airness.governance;

/**
 * A stable release newer than the version an owning project declares.
 *
 * @param declared the declaration and its owner
 * @param latest   the latest stable release using the declared version scheme
 */
public record VersionUpdate(OwnedCoordinate declared, String latest) {

    /**
     * The update in a form that keeps its owning project visible.
     *
     * @return the owner, artifact, declared version, and available version
     */
    public String report() {
        DeclaredCoordinate coordinate = this.declared.coordinate();
        return "[%s] %s:%s %s -> %s".formatted(
            this.declared.owner(), coordinate.groupId(), coordinate.artifactId(),
            coordinate.version(), this.latest
        );
    }
}
