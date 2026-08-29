package eu.ciechanowiec.airness.governance;

/**
 * One dependency of a module, as the effective Maven model resolved it.
 *
 * <p>The effective model rather than the raw pom, which is the opposite of what {@link MavenModelPolicy}
 * reads and right for the opposite reason. That policy asks who wrote a declaration, so erasing the pom
 * it came from would erase the question. These rules ask what will be on the classpath, and a starter a
 * project declares in its own aggregator is on the classpath of every module under it however the
 * declaration got there.
 *
 * <p>Held as a record rather than as Maven's own type so that the rules reading it stay in
 * {@code airness-governance}, which declares no Maven dependency and is unit-tested without one.
 *
 * @param groupId    the group the dependency is declared under
 * @param artifactId the artifact name
 * @param optional   whether the declaration is optional, which is what keeps it out of what depends on
 *                   this module
 */
public record SpringDependency(String groupId, String artifactId, boolean optional) {
}
