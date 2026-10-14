package eu.ciechanowiec.airness.governance;

/**
 * One module's use of a resolved artifact, without discarding another module's scope.
 *
 * @param module   reactor module coordinate
 * @param scope    Maven scope, or build for a plugin input
 * @param relation direct or transitive
 */
public record ReportUse(String module, String scope, String relation) {
}
