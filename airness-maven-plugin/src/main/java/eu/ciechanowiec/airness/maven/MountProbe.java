package eu.ciechanowiec.airness.maven;

import java.nio.file.Path;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Reads a bind mount from inside the image a Docker check runs, before the check is trusted with it.
 *
 * <p>The daemon answering is not the daemon seeing the files. A bind mount the host refuses to serve
 * arrives inside the container as a tree whose every open fails, and a folder the host does not share
 * with the machine Docker runs on arrives as an empty directory. A scanner reading either reports on
 * nothing, or exits the way it exits for a finding.
 */
@UtilityClass
final class MountProbe {

    private static final String REPOSITORY = "/repo";
    private static final String INPUT = "/input";
    /**
     * The listing proves the directory can be walked, the Git pointer proves a file can be opened whether
     * .git is a directory or the file a worktree leaves behind, and the top-level files prove it was not
     * one lucky file. The shell is the POSIX one, because the gitleaks image carries busybox and the
     * Qodana image carries dash.
     */
    private static final String REPOSITORY_PROBE = "set -e; cd /repo; ls -A . > /dev/null;"
        + " if [ -d .git ]; then cat .git/HEAD > /dev/null; else cat .git > /dev/null; fi;"
        + " find . -maxdepth 1 -type f -exec cat {} + > /dev/null";
    /**
     * An unshared folder arrives empty rather than as an error, so the listing must name something and
     * the first file found must open. The directory is the first argument, which keeps the same script
     * runnable against a host directory.
     */
    private static final String INPUT_PROBE = "set -e; cd \"$1\"; ls -A . | grep -q .;"
        + " file=\"$(find . -type f | head -n 1)\"; [ -n \"$file\" ]; cat \"$file\" > /dev/null";
    private static final String UNREADABLE = " cannot be read inside a container, so this"
        + " check would report on nothing. Docker mounted it read-only and a shell in the image could not list"
        + " it or read its files. On macOS with Colima this is the privacy protection of Downloads, Desktop and"
        + " Documents: grant the terminal that starts Colima access to the folder under System Settings,"
        + " Privacy and Security, Files and Folders, then restart Colima, or keep the repository outside those"
        + " folders.";
    private static final String EMPTY = " is mounted into the check but arrives empty or unreadable inside"
        + " a container, so the check would run without it and report on what it lacks. Docker shares only some"
        + " host folders with the machine it runs on, and a folder outside them arrives as an empty directory. On"
        + " macOS with Colima only the home directory is shared by default: keep the directory under it, for the"
        + " local Maven repository by pointing maven.repo.local there, or add the directory to the mounts in"
        + " Colima's colima.yaml and restart Colima.";

    static List<String> repositoryCommand(Path root, String image) {
        return List.of(
            "docker", "run", "--rm", "-v", root + ":" + REPOSITORY + ":ro",
            "--entrypoint", "/bin/sh", image, "-c", REPOSITORY_PROBE
        );
    }

    static List<String> inputCommand(Path input, String image) {
        return List.of(
            "docker", "run", "--rm", "-v", input + ":" + INPUT + ":ro",
            "--entrypoint", "/bin/sh", image, "-c", INPUT_PROBE, "sh", INPUT
        );
    }

    static String unreadableRepository(Path root) {
        return "The repository at " + root + UNREADABLE;
    }

    static String emptyInput(Path input) {
        return "The directory at " + input + EMPTY;
    }
}
