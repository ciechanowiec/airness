package eu.ciechanowiec.airness.web;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * The accessibility rules themselves, read off the test classpath as the text a browser is handed.
 *
 * <p>The rules ship as a web archive carrying one script and no class, so there is nothing here to
 * call and the script is read by name. The name carries the version, which is why the version is read
 * from the archive rather than written into this file: a version in a string here is a second place
 * for it to be pinned, and a project that upgraded the archive would be handed a name that no longer
 * exists.
 *
 * <p>This is what every project of the fleet used to hold a copy of, each with the version typed into
 * it. Reading it from one place is the smaller half of why this artifact exists. Asking the rules the
 * whole question is the larger half, and that is {@link AxeAudit}.
 */
@UtilityClass
public final class AxeLibrary {

    private static final String COORDINATES = "META-INF/maven/org.webjars.npm/axe-core/pom.properties";

    private static final String VERSION = "version";

    /**
     * Answers the rules as the text to run in a browser before asking anything of a page.
     *
     * @return the whole of the rules, as a script
     * @throws IllegalStateException when the rules are not on the test classpath at all, which says
     *                               the project declared no dependency on them rather than that a
     *                               page is clean
     */
    @SneakyThrows
    public static String rules() {
        try (
            InputStream reading = stream(
                "META-INF/resources/webjars/axe-core/%s/axe.min.js".formatted(version())
            )
        ) {
            return new String(reading.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Answers the version of the rules that is on the test classpath, which a report names so that
     * two runs disagreeing can be told apart from two versions disagreeing.
     *
     * @return the version of the rules
     * @throws IllegalStateException when the rules are not on the test classpath at all
     */
    @SneakyThrows
    public static String version() {
        Properties coordinates = new Properties();
        try (InputStream reading = stream(COORDINATES)) {
            coordinates.load(reading);
        }
        return named(coordinates);
    }

    // Package-private so that the archive naming no version is a case a test can reach. It is the
    // one failure here that cannot be produced by asking for a resource that is absent, because the
    // archive is present and its contents are not this project's to change.
    static String named(Properties coordinates) {
        return Optional.ofNullable(coordinates.getProperty(VERSION))
            .filter(named -> !named.isBlank())
            .orElseThrow(
                () -> new IllegalStateException(
                    "The accessibility rules are on the test classpath and name no version, so the script "
                        + "they publish cannot be located"
                )
            );
    }

    // Package-private for the same reason: what this product does when the rules are absent is a
    // behaviour worth asserting, and the rules are present wherever this module's own tests run.
    @SneakyThrows
    static InputStream stream(String resource) {
        URL located = Optional.ofNullable(AxeLibrary.class.getClassLoader().getResource(resource))
            .orElseThrow(
                () -> new IllegalStateException(
                    "The accessibility rules are not on the test classpath. Declare org.webjars.npm:axe-core "
                        + "at test scope, which the Spring parent already supplies: " + resource
                )
            );
        return located.openStream();
    }
}
