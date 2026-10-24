package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every file the harness owns is where its policy says it should be, with the bytes it should have
 * where the policy owns those bytes.
 *
 * <p>Which files apply is decided per site from the capabilities present there: the repository root
 * carries every capability present anywhere in the repository, and each declared node project carries
 * its own. A shared root file therefore holds one block for each stack in the repository, and a frontend
 * inside a Java module still receives the node files.
 *
 * <p>Seven disagreements are reported separately, because each of them names a different repair. A
 * pinned file or a set of managed blocks that drifted and a seeded file that is missing are all repairs
 * to run, but only the first has canonical content to compare. A tool-specific instruction file stating
 * rules of its own is prose to move into {@code AGENTS.md}. An opt-out that no longer differs from
 * canonical is a line to delete, and without that half the opt-out list rots into a blanket exemption.
 * An opt-out naming a path the harness does not own is a typo, and a typo in an exemption list reads as
 * an exemption that works. An opt-out naming a file of managed blocks, such as a {@code .gitignore} that
 * was once only seeded, is a line to delete, since such a file can no longer be taken over.
 *
 * <p>The reason for an opt-out is not checked and cannot be. It lives as a comment beside the property
 * and binds by whoever reads the pom, which is worth saying plainly rather than implying a check that
 * does not exist. A file of managed blocks cannot be taken over at all, since the project already owns
 * every line outside them.
 */
public final class AssetCheck {

    private static final String DRIFTED = "Files the harness owns that this project changed or is missing";
    private static final String MISSING = "Seeded files that must exist but are missing";
    private static final String PRESENT = "Files that must not be in the tree, since the harness supplies them";
    private static final String STATED = "Agent instruction files that state rules instead of pointing at AGENTS.md";
    private static final String SETTLED = "Opt-outs that no longer differ from canonical, so delete these";
    private static final String UNKNOWN = "Opt-outs naming a path the harness does not own, which is a typo";
    private static final String SECTIONED
        = "Opt-outs naming a file now managed in sections, which can no longer be opted out, so delete these";
    private static final String INSTRUCTIONS = "AGENTS.md";

    private final Path root;
    private final AssetCatalogue catalogue;
    private final Set<String> unmanaged;
    private final AssetPlan plan;

    /**
     * Checks the repository-root files of a repository holding only the generic capability.
     *
     * @param root      the working tree root
     * @param catalogue the files the harness owns
     * @param unmanaged repository-relative paths this project has taken over
     */
    public AssetCheck(Path root, AssetCatalogue catalogue, Collection<String> unmanaged) {
        this(root, catalogue, unmanaged, List.of(AssetSite.root(Set.of())));
    }

    /**
     * Reads nothing yet, holding only where to look, what applies where, and what to leave alone.
     *
     * @param root      the working tree root
     * @param catalogue the files the harness owns
     * @param unmanaged repository-relative paths this project has taken over
     * @param sites     the directories that receive files, with the capabilities present in each
     */
    public AssetCheck(
        Path root, AssetCatalogue catalogue, Collection<String> unmanaged, Collection<AssetSite> sites
    ) {
        this.root = root;
        this.catalogue = catalogue;
        this.unmanaged = Set.copyOf(unmanaged);
        this.plan = new AssetPlan(catalogue, sites);
    }

    /**
     * The ways a project and the files the harness owns can disagree.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(DRIFTED, Stream.concat(this.drifted(), this.staleSections()).toList()),
            new Findings(MISSING, this.missing()),
            new Findings(PRESENT, this.present()),
            new Findings(STATED, this.stated()),
            new Findings(SETTLED, this.settled()),
            new Findings(UNKNOWN, this.unknown()),
            new Findings(SECTIONED, this.sectioned())
        );
    }

    /**
     * Whether the repository-root file at a managed path is byte-for-byte what the harness ships.
     *
     * @param asset the managed file
     * @return whether the project's copy matches
     */
    public boolean matches(ManagedAsset asset) {
        return this.matches(new AssetPlan.Placement(asset.path(), asset));
    }

    private boolean matches(AssetPlan.Placement placement) {
        Optional<byte[]> shipped = this.catalogue.canonical(placement.asset());
        Optional<byte[]> held = this.held(placement.file());
        return shipped.isPresent() && held.isPresent() && Arrays.equals(shipped.get(), held.get());
    }

    private Stream<String> drifted() {
        return this.managed(AssetPolicy.PINNED)
            .filter(placement -> !this.matches(placement))
            .map(placement -> placement.file() + " (run mvn airness:assets-sync to restore it)");
    }

    private Stream<String> staleSections() {
        return this.plan.sections().entrySet().stream()
            .filter(section -> this.text(section.getKey()).filter(held -> current(held, section)).isEmpty())
            .map(section -> section.getKey() + " (run mvn airness:assets-sync to restore its managed blocks)");
    }

    private List<String> missing() {
        return this.managed(AssetPolicy.SEED)
            .filter(placement -> !Files.isRegularFile(this.path(placement.file()), LinkOption.NOFOLLOW_LINKS))
            .map(placement -> placement.file() + " (run mvn airness:assets-sync to create it)")
            .toList();
    }

    private List<String> present() {
        return this.managed(AssetPolicy.FORBIDDEN)
            .filter(placement -> Files.exists(this.path(placement.file()), LinkOption.NOFOLLOW_LINKS))
            .map(placement -> placement.file() + " (delete it, the harness supplies this file already)")
            .toList();
    }

    private List<String> stated() {
        return this.managed(AssetPolicy.REFERENCE)
            .filter(placement -> this.text(placement.file()).filter(AssetCheck::statesRules).isPresent())
            .map(placement -> placement.file() + " (reduce it to a line that points at " + INSTRUCTIONS + ")")
            .toList();
    }

    private List<String> settled() {
        return this.plan.placements(AssetPolicy.PINNED)
            .filter(placement -> this.unmanaged.contains(placement.file()) && this.matches(placement))
            .map(AssetPlan.Placement::file)
            .toList();
    }

    private List<String> sectioned() {
        Set<String> sections = this.sectionFiles();
        return this.unmanaged.stream().filter(sections::contains).sorted().toList();
    }

    private Set<String> sectionFiles() {
        return Stream.concat(
            this.plan.sections().keySet().stream(),
            this.catalogue.assets().stream()
                .filter(asset -> asset.location() == AssetLocation.ROOT && asset.policy() == AssetPolicy.SECTION)
                .map(ManagedAsset::path)
        ).collect(Collectors.toUnmodifiableSet());
    }

    private List<String> unknown() {
        Set<String> owned = Stream.concat(
            Stream.of(AssetPolicy.PINNED, AssetPolicy.SEED, AssetPolicy.FORBIDDEN, AssetPolicy.REFERENCE)
                .flatMap(this.plan::placements)
                .map(AssetPlan.Placement::file),
            this.catalogue.assets().stream()
                .filter(asset -> asset.location() == AssetLocation.ROOT && asset.policy() != AssetPolicy.SECTION)
                .map(ManagedAsset::path)
        ).collect(Collectors.toUnmodifiableSet());
        Set<String> sections = this.sectionFiles();
        return this.unmanaged.stream()
            .filter(path -> !owned.contains(path) && !sections.contains(path))
            .sorted()
            .toList();
    }

    private Stream<AssetPlan.Placement> managed(AssetPolicy policy) {
        return this.plan.placements(policy).filter(placement -> !this.unmanaged.contains(placement.file()));
    }

    private static boolean current(String held, Map.Entry<String, String> section) {
        return ManagedSections.current(held, section.getValue());
    }

    // Every line that says anything must say the same thing: read AGENTS.md. A line that does not name it
    // is this file stating a rule of its own, and an empty file points nowhere.
    private static boolean statesRules(String content) {
        List<String> stated = content.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        return stated.isEmpty() || !stated.stream().allMatch(line -> line.contains(INSTRUCTIONS));
    }

    private Path path(String file) {
        return this.root.resolve(file);
    }

    private Optional<String> text(String file) {
        return this.held(file).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    private Optional<byte[]> held(String file) {
        return Optional.of(this.path(file))
            .filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
            .map(AssetCheck::bytes);
    }

    private static byte[] bytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }
}
