package eu.ciechanowiec.airness.governance;

/**
 * The identity of one module of a governed build.
 *
 * <p>The model version is the one the module's own pom writes, since it is never inherited. The coordinates are
 * the effective ones, so a group the module inherits, or a version a property spells, is judged by what it
 * resolves to.
 *
 * @param modelVersion the model version the pom writes
 * @param groupId      the effective groupId
 * @param artifactId   the artifactId
 * @param version      the effective version
 * @param directory    the name of the directory the module's pom lies in
 * @param root         whether the module lies at the repository root, whose directory a clone may name freely
 */
public record ModuleIdentity(
    String modelVersion, String groupId, String artifactId, String version, String directory, boolean root
) {
}
