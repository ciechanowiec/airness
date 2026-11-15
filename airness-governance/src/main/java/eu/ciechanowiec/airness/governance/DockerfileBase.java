package eu.ciechanowiec.airness.governance;

import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * The registry image a Dockerfile's last stage is built on, which a shipped image takes its system packages
 * from and whose newer release is the repair for one it ships vulnerable.
 */
@UtilityClass
public final class DockerfileBase {

    /**
     * Reads the base the last stage ships on, following every earlier stage it names, with {@code ARG}
     * defaults substituted. A reference that still carries a variable no default gives is returned as written.
     *
     * @param dockerfile the Dockerfile
     * @return the reference, empty when the last stage starts from {@code scratch} or the file has no
     *         {@code FROM}
     */
    public static Optional<String> of(String dockerfile) {
        return DockerfileReader.shippedBase(dockerfile);
    }
}
