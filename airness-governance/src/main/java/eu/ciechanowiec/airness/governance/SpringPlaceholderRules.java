package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * The property keys production code reads through a placeholder, read against the configuration the
 * artifact ships with.
 *
 * <p>A placeholder with no default fails the startup of any environment that does not supply the key,
 * which is the right behaviour and the reason a test that boots the application seems to prove the key
 * exists. It proves it for the test's own configuration. A key the test profile declares and the base
 * file does not is satisfied in every test and missing in production, and the build that shipped it was
 * green.
 *
 * <p>So the key is read against the base configuration file alone, which is the one every environment
 * starts from, and against what the classpath publishes, since a key a dependency declares has a value
 * whether or not the project wrote one. A placeholder carrying a default has answered the question
 * itself. A module shipping no base configuration is passed over, because a library reading a key
 * relies on the application that declares it, and that is the application module's file to hold.
 */
@UtilityClass
final class SpringPlaceholderRules {

    private static final Pattern ANNOTATION = Pattern.compile("@\\w+\\s*\\(");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)}");

    /**
     * Every placeholder an annotation of the source reads that nothing the artifact ships declares.
     *
     * @param source   the source as written
     * @param base     the base configuration files of the module, already read
     * @param metadata what the classpath publishes
     * @return one offence per undeclared key, by line
     */
    static List<String> undeclaredPlaceholders(
        CharSequence source, Collection<SpringConfiguration> base, SpringMetadata metadata
    ) {
        String code = JavaCode.blanked(source);
        String read = JavaCode.withoutComments(source);
        return ANNOTATION.matcher(code).results()
            .flatMap(annotation -> placeholders(code, read, annotation))
            .filter(placeholder -> !declared(placeholder.key(), base, metadata))
            .map(placeholder -> offence(source, placeholder))
            .toList();
    }

    private static Stream<Placeholder> placeholders(String code, String read, MatchResult annotation) {
        int opens = annotation.end() - 1;
        int closes = SpringMembers.closing(code, opens);
        return PLACEHOLDER.matcher(read.substring(opens + 1, closes)).results()
            .map(found -> new Placeholder(opens + 1 + found.start(1), found.group(1).strip()));
    }

    private static boolean declared(
        String key, Collection<SpringConfiguration> base, SpringMetadata metadata
    ) {
        return base.stream().anyMatch(file -> file.declared(key).isPresent()) || metadata.known(key);
    }

    private static String offence(CharSequence source, Placeholder placeholder) {
        return "line " + JavaCode.lineOf(source, placeholder.at()) + ": the placeholder reads "
            + placeholder.key() + ", which no base configuration file of the module declares and nothing on"
            + " the classpath binds, so an environment the test profile does not stand in for refuses to"
            + " start; declare the key in the base file, reading its value from the environment"
            + " through a placeholder where that is where the value lives";
    }

    private record Placeholder(int at, String key) {
    }
}
