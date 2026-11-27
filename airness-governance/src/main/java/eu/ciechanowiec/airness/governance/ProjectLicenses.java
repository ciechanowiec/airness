package eu.ciechanowiec.airness.governance;

import java.util.Locale;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * The names a project may give its own license: an identifier the SPDX license list still recommends, or the one
 * reference Airness reserves for code nobody else may use.
 *
 * <p>Every license on the SPDX list hands rights to whoever receives the code, so the list has no entry for code
 * that is closed. {@value #PROPRIETARY} states that instead: all rights are reserved, and whatever use is granted
 * comes from a contract rather than from a public license. It is a label rather than a license text, so no
 * project writes one. Any other {@code LicenseRef-} is refused, because a reference the project invents names
 * terms nobody else can look up, and the SPDX list already holds every public license a project would choose.
 *
 * <p>{@code Unlicense} is refused although the list carries it. It places the code in the public domain, and the
 * name reads like the npm convention {@code UNLICENSED}, which means the opposite.
 */
@UtilityClass
public final class ProjectLicenses {

    /**
     * The license name of a project whose code no public license covers.
     */
    public static final String PROPRIETARY = "LicenseRef-Proprietary";

    private static final String REFERENCE = "licenseref-";
    private static final String PUBLIC_DOMAIN = "unlicense";

    /**
     * Why a license name cannot name the project's own license.
     *
     * @param name the license name as the pom or the command line writes it
     * @param list the SPDX license list to name it from
     * @return the refusal, or nothing when the name is admitted
     */
    public static Optional<String> refusal(String name, LicenseCatalogue list) {
        String folded = name.strip().toLowerCase(Locale.ROOT);
        Optional<String> unusable = unusable(name, folded);
        if (unusable.isPresent()) {
            return unusable;
        }
        return folded.startsWith(REFERENCE) ? reference(name) : listed(name, folded, list);
    }

    private static Optional<String> unusable(String name, String folded) {
        if (folded.isEmpty()) {
            return Optional.of("a license has no name; name it " + PROPRIETARY + " or by an SPDX identifier");
        }
        return Optional.of(folded)
            .filter(PUBLIC_DOMAIN::equals)
            .map(
                _ -> name + " places the code in the public domain; name " + PROPRIETARY
                    + " for code that is not given away"
            );
    }

    private static Optional<String> listed(String name, String folded, LicenseCatalogue list) {
        Optional<String> identifier = list.identifiers().stream()
            .filter(candidate -> candidate.toLowerCase(Locale.ROOT).equals(folded))
            .findFirst();
        if (identifier.isEmpty()) {
            return Optional.of(
                name + " is no current identifier of SPDX license list " + list.version() + "; name one, or "
                    + PROPRIETARY
            );
        }
        return identifier.filter(spelling -> !spelling.equals(name))
            .map(spelling -> "write " + name + " as " + spelling);
    }

    private static Optional<String> reference(String name) {
        if (PROPRIETARY.equals(name)) {
            return Optional.empty();
        }
        return Optional.of(
            PROPRIETARY.equalsIgnoreCase(name.strip())
                ? "write " + name + " as " + PROPRIETARY
                : name + " is a license reference of the project's own; name " + PROPRIETARY
                    + " or an SPDX identifier"
        );
    }
}
