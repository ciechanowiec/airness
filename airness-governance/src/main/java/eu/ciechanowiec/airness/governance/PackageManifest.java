package eu.ciechanowiec.airness.governance;

import java.util.Set;

/**
 * What a {@code package.json} declares that an instruction file may name: its npm scripts, and the packages it
 * depends on, whose subpaths are module specifiers rather than repository paths.
 *
 * @param scripts      the script names
 * @param dependencies the names of every declared dependency, of whatever kind
 */
public record PackageManifest(Set<String> scripts, Set<String> dependencies) {

    /**
     * Copies both sets, so the manifest cannot change after it was read.
     *
     * @param scripts      the script names
     * @param dependencies the dependency names
     */
    public PackageManifest {
        scripts = Set.copyOf(scripts);
        dependencies = Set.copyOf(dependencies);
    }
}
