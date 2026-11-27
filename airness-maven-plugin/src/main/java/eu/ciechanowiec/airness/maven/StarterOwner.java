package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.LicenseCatalogue;
import eu.ciechanowiec.airness.governance.ProjectLicenses;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Who an application made from a starter belongs to, and on what terms: the metadata its root pom states rather
 * than inherits from Airness.
 *
 * @param organization  who owns the application, written as its organization and its developer
 * @param url           the https address of the application's repository, which its pom names as both its home
 *                      page and its source
 * @param license       the license of the application, {@link ProjectLicenses#PROPRIETARY} unless one is given
 * @param inceptionYear the year the application begins, which is the year it is made
 */
record StarterOwner(String organization, String url, String license, Year inceptionYear) {

    // An https address without a trailing slash or .git, and without what would need escaping in a pom.
    private static final Pattern URL = Pattern.compile("https://[^\\s<>\"'&]+(?<!/)(?<!\\.git)");
    private static final String GIT = "scm:git:";

    /**
     * Answers the owner the properties of a command line name: {@code airness.organization} and {@code airness.url},
     * and optionally {@code airness.license}.
     *
     * @param properties the properties of the command line
     * @param year       the year the application is made
     * @return the owner, which may still hold a value {@link #problems(LicenseCatalogue)} refuses
     */
    static StarterOwner of(Properties properties, Year year) {
        return new StarterOwner(
            StarterRequest.given(properties, "organization").orElse(""),
            StarterRequest.given(properties, "url").orElse(""),
            StarterRequest.given(properties, "license").orElse(ProjectLicenses.PROPRIETARY),
            year
        );
    }

    /**
     * Answers why the owner cannot be written into a pom, one sentence for each value it refuses.
     *
     * @param licenses the SPDX license list the license is named from
     * @return the refusals, none for an owner that can be written
     */
    List<String> problems(LicenseCatalogue licenses) {
        Optional<String> unowned = this.organization.isBlank()
            ? Optional.of("airness.organization is not set, but it must name who owns the application")
            : Optional.empty();
        Optional<String> address = URL.matcher(this.url).matches()
            ? Optional.empty()
            : Optional.of(
                "airness.url is '" + this.url + "', but it must be the https address of the application's repository,"
                    + " without a trailing slash or .git, such as https://git.sample.org/task-tracker"
            );
        Optional<String> unlicensed = ProjectLicenses.refusal(this.license, licenses)
            .map(refusal -> "airness.license is '" + this.license + "', but " + refusal);
        return Stream.of(unowned, address, unlicensed).flatMap(Optional::stream).toList();
    }

    /**
     * Answers the address Git clones the application's repository from.
     *
     * @return the repository address as a Maven source control connection
     */
    String connection() {
        return GIT + this.url + ".git";
    }
}
