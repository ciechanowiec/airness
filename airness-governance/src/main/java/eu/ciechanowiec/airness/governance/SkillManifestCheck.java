package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Every agent skill manifest under {@code .claude/skills} carries the frontmatter an agent needs to
 * discover and invoke the skill.
 *
 * <p>The manifests are found in the committable tree, so a new skill is checked before it is committed. A
 * repository with no skill passes over an empty set.
 */
public final class SkillManifestCheck {

    private static final Path SKILLS = Path.of(".claude", "skills");
    private static final String MANIFEST = "SKILL.md";
    private static final String BROKEN
        = "Agent skill manifests whose frontmatter an agent cannot use (repair the frontmatter)";

    private final Path root;
    private final List<Path> manifests;

    /**
     * Names the working tree whose skill manifests are read.
     *
     * @param root the working tree root
     */
    public SkillManifestCheck(Path root) {
        this.root = root;
        this.manifests = Repository.trackedFiles(root).stream()
            .filter(file -> root.relativize(file).startsWith(SKILLS))
            .filter(file -> MANIFEST.equals(String.valueOf(file.getFileName())))
            .sorted()
            .toList();
    }

    /**
     * How many skill manifests the check reads.
     *
     * @return the manifest count
     */
    public int scanned() {
        return this.manifests.size();
    }

    /**
     * The frontmatter defects of every manifest, each naming its file.
     *
     * @return one verdict
     */
    public List<Findings> findings() {
        List<String> broken = this.manifests.stream()
            .flatMap(
                manifest -> SkillManifest.parse(Repository.readText(manifest).orElse(""))
                    .violations(directory(manifest)).stream()
                    .map(violation -> this.root.relativize(manifest) + " " + violation)
            ).toList();
        return List.of(new Findings(BROKEN, broken));
    }

    private static String directory(Path manifest) {
        return Optional.ofNullable(manifest.getParent())
            .map(Path::getFileName)
            .map(String::valueOf)
            .orElse("");
    }
}
