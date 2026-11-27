package eu.ciechanowiec.airness.governance;

import java.util.List;

/**
 * Refuses a module whose metadata still comes from a pom Airness publishes.
 *
 * <p>A module below the root receives its metadata from the root, which {@link RootMetadataCheck} holds to the
 * project's own. A module that inherits an Airness parent directly never passes through the root, so its licenses,
 * developers, address and source control come from Airness unless the module declares them itself. The question
 * is asked of where each value comes from rather than of what it says, so a project that chose the license Airness
 * happens to carry is never refused for it.
 */
public final class InheritedMetadataCheck {

    private final List<MetadataSource> sources;

    /**
     * Records where each metadata field of the module comes from.
     *
     * @param sources the nearest declaring pom of each field the module's chain declares
     */
    public InheritedMetadataCheck(List<MetadataSource> sources) {
        this.sources = List.copyOf(sources);
    }

    /**
     * Reports every metadata field that comes from a pom Airness publishes.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(
                "Declare the metadata Maven would otherwise inherit from Airness",
                this.sources.stream()
                    .filter(MetadataSource::harness)
                    .map(source -> "<" + source.field() + "> comes from " + source.coordinate())
                    .toList()
            )
        );
    }
}
