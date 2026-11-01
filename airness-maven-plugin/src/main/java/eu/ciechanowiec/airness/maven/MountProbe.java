package eu.ciechanowiec.airness.maven;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A container run that must succeed before a Docker check is trusted with its mounts: it reads a bind mount
 * from inside the image the check runs.
 *
 * <p>The daemon answering is not the daemon seeing the files. A bind mount the host refuses to serve
 * arrives inside the container as a tree whose every open fails, and a folder the host does not share
 * with the machine Docker runs on arrives as an empty directory. A scanner reading either reports on
 * nothing, or exits the way it exits for a finding.
 *
 * @param command    the docker command that reads them
 * @param mount      the host directory it reads, named when the container cannot start
 * @param unreadable what the build says when the command fails
 */
record MountProbe(List<String> command, Path mount, String unreadable) {

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
    /**
     * Git finds the repository and, when HEAD names a commit, reads it. The first argument is where the
     * working tree is inside the container, the second says whether there is a commit to read, because an
     * unborn HEAD is a repository with nothing to scan rather than one that cannot be read.
     */
    private static final String HISTORY_PROBE = "set -e; git -C \"$1\" rev-parse --git-dir > /dev/null;"
        + " if [ \"$2\" = commits ]; then git -C \"$1\" log -1 --format=%H > /dev/null; fi";
    private static final String UNREADABLE_HISTORY = " cannot be read by Git inside a container,"
        + " so this check would scan no commit and report a clean history. A worktree made by git worktree add"
        + " keeps its history in the main clone's .git directory, which the check mounts beside it: keep both"
        + " where Docker can read them, on macOS with Colima under the home directory, or verify a standalone"
        + " clone.";
    private static final String UNREADABLE = " cannot be read inside a container, so this"
        + " check would report on nothing. Docker mounted it read-only and a shell in the image could not list"
        + " it or read its files. On macOS with Colima this is the privacy protection of Downloads, Desktop and"
        + " Documents: grant the terminal that starts Colima access to the folder under System Settings,"
        + " Privacy and Security, Files and Folders, then restart Colima, or keep the repository outside those"
        + " folders. A folder Docker does not share with the machine it runs on arrives empty instead, and with"
        + " Colima only the home directory is shared by default: keep the repository under it, or add its folder"
        + " to the mounts in Colima's colima.yaml and restart Colima.";
    private static final String EMPTY = " is mounted into the check but arrives empty or unreadable inside"
        + " a container, so the check would run without it and report on what it lacks. Docker shares only some"
        + " host folders with the machine it runs on, and a folder outside them arrives as an empty directory. On"
        + " macOS with Colima only the home directory is shared by default: keep the directory under it, for the"
        + " local Maven repository by pointing maven.repo.local there, or add the directory to the mounts in"
        + " Colima's colima.yaml and restart Colima.";

    MountProbe {
        command = List.copyOf(command);
    }

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

    static List<String> historyCommand(HistoryMount mount, String image, boolean commits) {
        return Stream.of(
            Stream.of("docker", "run", "--rm"), mount.volumes().stream(),
            Stream.of(
                "--entrypoint", "/bin/sh", image, "-c", HISTORY_PROBE, "sh", mount.path(),
                commits ? "commits" : "unborn"
            )
        ).flatMap(Function.identity()).toList();
    }

    static String unreadableHistory(Path root) {
        return "The Git history of the repository at " + root + UNREADABLE_HISTORY;
    }

    static String unreadableRepository(Path root) {
        return "The repository at " + root + UNREADABLE;
    }

    static String emptyInput(Path input) {
        return "The directory at " + input + EMPTY;
    }

    /**
     * The probe that reads a directory mounted besides the repository.
     *
     * @param input the host directory
     * @param image the image the check runs
     * @return the probe, which names the directory and the fix when it arrives empty
     */
    static MountProbe input(Path input, String image) {
        return new MountProbe(inputCommand(input, image), input, emptyInput(input));
    }
}
