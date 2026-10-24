package eu.ciechanowiec.airness.governance;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Where every managed file goes in one repository, worked out from the catalogue and the sites that
 * receive files.
 *
 * <p>Two sites can be one directory: a repository whose root is also its node project receives the node
 * block of its {@code AGENTS.md} once as a root contribution and once as a node project. A file holds one
 * block per owner, so the two are one placement, and the node project's body wins, because it is the one
 * written for the directory the file is in.
 */
final class AssetPlan {

    private final AssetCatalogue catalogue;
    private final List<Placement> placements;

    /**
     * Places every file the catalogue selects for each site.
     *
     * @param catalogue the files the harness owns
     * @param sites     the directories that receive files, with the capabilities present in each
     */
    AssetPlan(AssetCatalogue catalogue, Collection<AssetSite> sites) {
        this.catalogue = catalogue;
        Map<String, Placement> unique = sites.stream()
            .flatMap(this::placed)
            .collect(
                Collectors.toMap(
                    placement -> placement.file() + '\t' + placement.asset().owner(),
                    Function.identity(),
                    // NODE_PROJECT is declared after ROOT, so the greater location is the more specific one.
                    BinaryOperator.maxBy(Comparator.comparing(placement -> placement.asset().location())),
                    LinkedHashMap::new
                )
            );
        this.placements = List.copyOf(unique.values());
    }

    /**
     * The placed files with one policy.
     *
     * @param policy the policy
     * @return the placements, in site order
     */
    Stream<Placement> placements(AssetPolicy policy) {
        return this.placements.stream().filter(placement -> placement.asset().policy() == policy);
    }

    /**
     * The leading text every placed {@link AssetPolicy#SECTION} file must start with.
     *
     * @return the composed blocks, keyed by repository-relative file
     */
    Map<String, String> sections() {
        return this.placements(AssetPolicy.SECTION)
            .collect(Collectors.groupingBy(Placement::file, LinkedHashMap::new, Collectors.toList()))
            .entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> this.compose(entry.getValue()),
                    (first, _) -> first,
                    LinkedHashMap::new
                )
            );
    }

    private String compose(Collection<Placement> claims) {
        return ManagedSections.compose(
            claims.stream()
                .sorted(Comparator.comparing(placement -> placement.asset().owner()))
                .map(this::block)
                .toList()
        );
    }

    private Stream<Placement> placed(AssetSite site) {
        return this.catalogue.select(site.location(), site.capabilities()).stream()
            .map(asset -> new Placement(site.file(asset), asset));
    }

    private String block(Placement placement) {
        ManagedAsset asset = placement.asset();
        return ManagedSections.block(asset.path(), asset.owner(), this.body(asset));
    }

    private String body(ManagedAsset asset) {
        return new String(
            this.catalogue.canonical(asset).orElseThrow(
                () -> new IllegalStateException(
                    "The harness ships no " + asset.owner().id() + " block for " + asset.path()
                )
            ),
            StandardCharsets.UTF_8
        );
    }

    /**
     * One managed file at one site.
     *
     * @param file  the repository-relative path it takes
     * @param asset the manifest row that placed it
     */
    record Placement(String file, ManagedAsset asset) {
    }
}
