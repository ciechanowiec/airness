package eu.ciechanowiec.airness.governance;

/**
 * What a pom says about the project rather than about its build: its name, what it is, where it lives, since when,
 * who owns it and where its source is kept. A value the model does not state is empty.
 *
 * <p>The same record carries a raw model and an effective one. Read from the raw model it says what the pom itself
 * declares, which is what decides whether a project stated a value or inherited it. Read from the effective model
 * it says what the value resolves to, which is what a value rule judges.
 *
 * <p>Held as a record rather than as Maven's own type so that the rules reading it stay in
 * {@code airness-governance}, which declares no Maven dependency.
 *
 * @param name          the human-readable name
 * @param description   what the project is
 * @param url           the project's home page
 * @param inceptionYear the year the project began
 * @param ownership     the licenses, developers and organization
 * @param scm           where the source is kept
 */
public record ProjectMetadata(
    String name, String description, String url, String inceptionYear, ProjectOwnership ownership,
    ScmCoordinates scm
) {
}
