package eu.ciechanowiec.airness.governance;

import java.util.Set;

/**
 * What a {@code package.json} declares that an instruction file may name, and what it installs.
 *
 * <p>An instruction file may name a subpath of any declared package, peers included, so the declared set reads
 * every dependency block. Whether a package is a Next.js application is decided by what it installs: its
 * runtime, development and optional dependencies, never a peer, which is a requirement on the consumer's
 * tree. The node gates read a package the same way, so the harness and the
 * gates agree about which packages are Next.js applications.
 *
 * @param scripts      the script names
 * @param dependencies the names of every declared dependency, of whatever kind
 * @param installed    the names of the dependencies the package installs, peers left out
 */
public record PackageManifest(Set<String> scripts, Set<String> dependencies, Set<String> installed) {

    /**
     * Copies every set, so the manifest cannot change after it was read.
     *
     * @param scripts      the script names
     * @param dependencies the dependency names
     * @param installed    the installed dependency names
     */
    public PackageManifest {
        scripts = Set.copyOf(scripts);
        dependencies = Set.copyOf(dependencies);
        installed = Set.copyOf(installed);
    }

    /**
     * A manifest that declares no peer, so every declared dependency is installed.
     *
     * @param scripts      the script names
     * @param dependencies the dependency names
     */
    public PackageManifest(Set<String> scripts, Set<String> dependencies) {
        this(scripts, dependencies, dependencies);
    }

    /**
     * Whether the package installs a dependency of this name.
     *
     * @param name the package name
     * @return true when it is a dependency, a development or an optional dependency
     */
    public boolean installs(String name) {
        return this.installed.contains(name);
    }
}
