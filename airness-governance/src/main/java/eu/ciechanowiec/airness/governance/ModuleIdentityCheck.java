package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Holds a module's identity to the shapes Maven and its registries expect.
 *
 * <p>The model version is the one Maven 3 reads. The group is a reverse domain the owner holds, never one reserved
 * for examples, so a project made from a template cannot ship under the template's name. The artifact is
 * lower-case words joined by hyphens and, below the root, names the directory the module lies in, so the reactor
 * and the tree read alike. The root is exempt from that, since a clone or a CI workspace names its directory
 * freely. The version is three numbers, optionally a SNAPSHOT.
 */
public final class ModuleIdentityCheck {

    private static final String MODEL_VERSION = "4.0.0";

    private final ModuleIdentity identity;

    /**
     * Records the identity the check reads.
     *
     * @param identity the module's identity
     */
    public ModuleIdentityCheck(ModuleIdentity identity) {
        this.identity = identity;
    }

    /**
     * Reports every part of the module's identity that departs from its expected shape.
     *
     * @return one group of findings per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings("Write modelVersion " + MODEL_VERSION, this.modelVersion()),
            new Findings("Name the group by a reverse domain the project's owner holds", this.group()),
            new Findings(
                "Name the artifact in lower-case words joined by hyphens, after its directory", this.artifact()
            ),
            new Findings("Version the project as major.minor.patch, optionally -SNAPSHOT", this.version())
        );
    }

    private List<String> modelVersion() {
        return Optional.of(this.identity.modelVersion())
            .filter(version -> !MODEL_VERSION.equals(version))
            .map(version -> this.label() + " writes modelVersion '" + version + "'")
            .stream().toList();
    }

    private List<String> group() {
        String group = this.identity.groupId();
        if (!MavenNames.reverseDomain(group)) {
            return List.of(this.label() + ": groupId " + group + " is not a reverse domain of lower-case segments");
        }
        return Optional.of(group)
            .filter(MavenNames::exampleDomain)
            .map(example -> this.label() + ": groupId " + example + " names an example domain, not an owner")
            .stream().toList();
    }

    private List<String> artifact() {
        String artifact = this.identity.artifactId();
        Stream<String> shape = MavenNames.kebab(artifact)
            ? Stream.empty()
            : Stream.of(this.label() + ": artifactId " + artifact + " is not lower-case words joined by hyphens");
        Stream<String> directory = this.identity.root() || artifact.equals(this.identity.directory())
            ? Stream.empty()
            : Stream.of(
                this.label() + ": artifactId " + artifact + " differs from its directory " + this.identity.directory()
            );
        return Stream.concat(shape, directory).toList();
    }

    private List<String> version() {
        return Optional.of(this.identity.version())
            .filter(version -> !MavenNames.plainVersion(version))
            .map(version -> this.label() + ": version " + version + " is not major.minor.patch")
            .stream().toList();
    }

    private String label() {
        return this.identity.groupId() + ':' + this.identity.artifactId();
    }
}
