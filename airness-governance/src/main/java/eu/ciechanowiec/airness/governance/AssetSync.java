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
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Writes catalogue-managed files into a project.
 *
 * <p>The lifecycle deliberately does not run this repair. Its read-only asset check fails on drift, and
 * this writer is a separate command so a successful verification describes the tree it was given.
 *
 * <p>Nothing is ever deleted. A file the harness forbids is reported by the check with the reason and
 * the remedy, and removing it is left to whoever put it there: a build tool deleting a developer's file
 * on their behalf is a worse failure than the one it would be fixing. For the same reason a file of
 * managed blocks keeps every project line: the blocks are replaced, and the rest is carried below them.
 */
public final class AssetSync {

    private final Path root;
    private final AssetCatalogue catalogue;
    private final Set<String> unmanaged;
    private final AssetPlan plan;

    /**
     * Reads nothing yet, holding only where to write, what applies where, and what to leave alone.
     *
     * @param root      the working tree root
     * @param catalogue the files the harness owns
     * @param unmanaged repository-relative paths this project has taken over
     * @param sites     the directories that receive files, with the layers present in each
     */
    public AssetSync(
        Path root, AssetCatalogue catalogue, Collection<String> unmanaged, Collection<AssetSite> sites
    ) {
        this.root = real(root);
        this.catalogue = catalogue;
        this.unmanaged = Set.copyOf(unmanaged);
        this.plan = new AssetPlan(catalogue, sites);
    }

    /**
     * Writes what is missing or has drifted, and reports what it wrote.
     *
     * <p>A pinned file is written whenever it differs. A seed is written only when it is absent, since
     * its body belongs to the project the moment it exists. A file of managed blocks gets the current
     * blocks at its start and keeps everything else.
     *
     * @return the repository-relative paths written, pinned and seeded files first, in site order
     * @throws IllegalStateException when the markers of a file of managed blocks do not pair up
     */
    public List<String> write() {
        Stream<AssetPlan.Placement> whole = Stream.concat(
            this.plan.placements(AssetPolicy.PINNED), this.plan.placements(AssetPolicy.SEED)
        );
        Stream<String> files = whole
            .filter(placement -> !this.unmanaged.contains(placement.file()))
            .filter(this::needsWriting)
            .map(this::materialize);
        Stream<String> sections = this.plan.sections().entrySet().stream()
            .filter(this::compose)
            .map(Map.Entry::getKey);
        return Stream.concat(files, sections).toList();
    }

    /**
     * Every pinned file the harness places, which the agent runtime is denied as a generated file is: the harness
     * writes each from its shipped bytes, and an edit made by hand is one the next sync takes back.
     *
     * @return the repository-relative paths, in site order, without those this project has taken over
     */
    public List<String> pinned() {
        return this.plan.placements(AssetPolicy.PINNED)
            .map(AssetPlan.Placement::file)
            .filter(file -> !this.unmanaged.contains(file))
            .distinct()
            .toList();
    }

    /**
     * Creates absent scaffolds during explicit initialization, preserving existing project content.
     *
     * @return the repository-relative paths written
     */
    public List<String> scaffold() {
        return this.plan.placements(AssetPolicy.SCAFFOLD)
            .filter(placement -> !this.unmanaged.contains(placement.file()))
            .filter(this::needsWriting)
            .map(this::materialize)
            .toList();
    }

    private boolean needsWriting(AssetPlan.Placement placement) {
        return placement.asset().policy() == AssetPolicy.PINNED
            ? !this.matches(placement)
            : !Files.exists(this.safe(placement.file()), LinkOption.NOFOLLOW_LINKS);
    }

    private boolean matches(AssetPlan.Placement placement) {
        Optional<byte[]> shipped = this.catalogue.canonical(placement.asset());
        Path file = this.safe(placement.file());
        return shipped.isPresent() && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
            && Arrays.equals(shipped.get(), bytes(file));
    }

    private String materialize(AssetPlan.Placement placement) {
        byte[] content = this.catalogue.canonical(placement.asset()).orElseThrow(
            () -> new IllegalStateException("The harness ships no bytes for " + placement.file())
        );
        store(this.safe(placement.file()), content);
        return placement.file();
    }

    private boolean compose(Map.Entry<String, String> section) {
        Path file = this.safe(section.getKey());
        String held = Files.exists(file, LinkOption.NOFOLLOW_LINKS)
            ? new String(bytes(file), StandardCharsets.UTF_8)
            : "";
        if (ManagedSections.current(held, section.getValue())) {
            return false;
        }
        String project = ManagedSections.projectPart(held).orElseThrow(
            () -> new IllegalStateException(
                section.getKey() + " has Airness markers that do not pair up; repair them manually"
            )
        );
        store(file, ManagedSections.write(section.getValue(), project).getBytes(StandardCharsets.UTF_8));
        return true;
    }

    private static void store(Path file, byte[] content) {
        try {
            Optional.ofNullable(file.getParent()).ifPresent(AssetSync::directory);
            Files.write(file, content);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write " + file, exception);
        }
    }

    private static byte[] bytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }

    private Path safe(String path) {
        Path target = this.root.resolve(path).normalize();
        if (!target.startsWith(this.root)) {
            throw new IllegalStateException("Managed asset escapes the repository root: " + path);
        }
        Path relative = this.root.relativize(target);
        IntStream.rangeClosed(1, relative.getNameCount())
            .mapToObj(index -> this.root.resolve(relative.subpath(0, index)))
            .filter(Files::isSymbolicLink)
            .findFirst()
            .ifPresent(AssetSync::rejectLink);
        return target;
    }

    private static void rejectLink(Path link) {
        throw new IllegalStateException("Managed asset path crosses a symbolic link: " + link);
    }

    private static void directory(Path parent) {
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create " + parent, exception);
        }
    }

    private static Path real(Path root) {
        try {
            return root.toRealPath();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not resolve repository root " + root, exception);
        }
    }
}
