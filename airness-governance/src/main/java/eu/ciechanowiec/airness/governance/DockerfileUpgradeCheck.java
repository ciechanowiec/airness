package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;

/**
 * No Dockerfile upgrades the system packages its base image holds.
 *
 * <p>A newer release of the base is the repair for a package a shipped image holds vulnerable, and the
 * compliance check names that release when one carries the fix. An upgrade inside the image patches whatever
 * one build happened to fetch instead, so the image no longer is the release its Dockerfile names. Every
 * tracked Dockerfile is read, the starters' and test fixtures' included, because an image anybody builds from
 * the repository is held to the same base.
 */
public final class DockerfileUpgradeCheck {

    private static final String HEADLINE = "Dockerfiles that upgrade the system packages of their base image (remove"
        + " the upgrade and move FROM to a newer release of the base image, which the compliance report names when"
        + " one fixes a vulnerability)";

    private final int scanned;
    private final List<String> offences;

    /**
     * Reads every tracked Dockerfile of the working tree once.
     *
     * @param root the working tree root
     */
    public DockerfileUpgradeCheck(Path root) {
        List<Path> dockerfiles = BlocklistFiles.dockerfiles(Repository.trackedFiles(root));
        this.scanned = dockerfiles.size();
        this.offences = dockerfiles.stream().flatMap(
            file -> offences(root.relativize(file).toString(), Repository.readText(file).orElse("")).stream()
        ).toList();
    }

    /**
     * How many Dockerfiles the check read, which a caller logs so the reach of a clean verdict is on record.
     *
     * @return the number of Dockerfiles read
     */
    public int scanned() {
        return this.scanned;
    }

    /**
     * Every upgrading command, each at the line its instruction starts on.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.offences));
    }

    /**
     * The upgrading commands of one Dockerfile, so a file the repository stores under another name, such as a
     * starter's, is judged by the same rule.
     *
     * @param name       the name the findings give the file
     * @param dockerfile the Dockerfile
     * @return one finding per upgrading command
     */
    static List<String> offences(String name, String dockerfile) {
        return DockerfileReader.packageUpgrades(dockerfile).stream()
            .map(command -> name + ':' + command.line() + " runs `" + command.value() + '`').toList();
    }
}
