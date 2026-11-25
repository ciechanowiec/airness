package eu.ciechanowiec.airness.governance;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One module's use of a resolved artifact, without discarding another module's scope.
 *
 * <p>The scope is a Maven scope for a Maven artifact, {@code build} for a build tool, and {@code production} or
 * {@code development} for an npm package: {@code production} when a workspace member reaches it through its
 * {@code dependencies} or {@code optionalDependencies}, so the product ships it, and {@code development} when only
 * {@code devDependencies} reach it.
 *
 * @param module   reactor module coordinate
 * @param scope    Maven scope, {@code build} for a build tool, or the npm use
 * @param relation {@code Direct} or {@code Transitive}
 */
public record ReportUse(String module, String scope, String relation) {

    /**
     * The scope an npm package a workspace member ships has.
     */
    public static final String PRODUCTION = "production";

    /**
     * The scope an npm package only development reaches has.
     */
    public static final String DEVELOPMENT = "development";

    /**
     * The scope a build tool has.
     */
    public static final String BUILD = "build";

    private static final Set<String> SHIPPED = Set.of("compile", "runtime", PRODUCTION);
    private static final Map<String, String> LABELS = Map.of(
        "compile", "Production", "runtime", "Production", PRODUCTION, "Production", "provided", "Provided",
        "system", "Provided", "test", "Test", BUILD, "Build", DEVELOPMENT, "Development"
    );

    /**
     * Whether this use puts the artifact into what the product ships: the production classpath of a Maven module,
     * or the production dependencies of a workspace member.
     *
     * @return whether the use is a production one
     */
    public boolean production() {
        return SHIPPED.contains(this.scope);
    }

    /**
     * The scope as a reader names it, the same for every ecosystem: {@code Production}, {@code Provided},
     * {@code Test}, {@code Build} or {@code Development}.
     *
     * @return the label
     */
    public String label() {
        return LABELS.getOrDefault(this.scope, capitalized(this.scope));
    }

    /**
     * The relation as a reader names it, {@code Direct} or {@code Transitive}, whatever case the evidence wrote.
     *
     * @return the label
     */
    public String relationLabel() {
        return capitalized(this.relation);
    }

    private static String capitalized(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
