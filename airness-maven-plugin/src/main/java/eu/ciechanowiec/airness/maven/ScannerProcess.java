package eu.ciechanowiec.airness.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Executes a pinned scanner with isolated configuration and bounded process lifetime.
 * The host captures tool output, so the container needs no writable bind mount.
 *
 * @param image immutable scanner image
 * @param tree  invocation inputs and output
 */
record ScannerProcess(String image, ScannerTree tree) {

    private static final Pattern PIN = Pattern.compile("^[a-z0-9][a-z0-9./_-]*:[^@]+@sha256:[a-f0-9]{64}$");

    List<String> command(String executable, List<String> arguments) {
        this.validate(executable);
        List<String> command = new ArrayList<>(
            List.of(
                "docker", "run", "--rm", "--init", "--cap-drop=ALL", "--security-opt=no-new-privileges",
                "--mount", "type=bind,source=" + this.tree.input() + ",target=/input,readonly",
                "--mount", "type=bind,source=" + this.tree.directory() + ",target=/output,readonly",
                "--workdir", "/input", "--entrypoint", executable
            )
        );
        command.addAll(List.of("--network", "none"));
        command.add(this.image);
        command.addAll(arguments);
        return List.copyOf(command);
    }

    List<String> probeCommand() {
        return this.command(
            "/bin/sh", List.of(
                "-c",
                "set -eu; test -f /input/.airness-scanner-input; find /input -type f -exec cat {} + > /dev/null"
            )
        );
    }

    void validate(String executable) {
        if (!PIN.matcher(this.image).matches()) {
            throw new IllegalArgumentException("Scanner image must have a version and immutable digest: " + this.image);
        }
        String tool = "python".equals(executable) ? "checkov" : executable;
        boolean probe = "/bin/sh".equals(executable);
        List<String> allowed = probe
            ? Stream.of("shellcheck", "checkov").map(ScannerTools::image).toList()
            : List.of(ScannerTools.image(tool));
        if (!allowed.contains(this.image)) {
            throw new IllegalArgumentException("The " + tool + " image is owned by the installed Airness policy");
        }
    }
}
