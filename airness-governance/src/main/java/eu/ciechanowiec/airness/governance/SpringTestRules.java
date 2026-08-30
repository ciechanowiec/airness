package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reports what a test suite asks the framework to arrange and the module never arranged.
 *
 * <p>The rule here is cross-file in a way the others are not, because the second half is outside Java
 * entirely. A profile is named in a test and answered by a file in a resource tree, and neither source
 * is wrong read alone, which is why it cannot be a rule over one file.
 *
 * <p>It reads {@link SpringTypes.Declared#quoted()} rather than the blanked code, since a profile name
 * is a value rather than structure, and blanking the literal takes the name away.
 *
 * <p>The two rules here are the same relation read in both directions: a profile a test names and no
 * file answers, and a file named after a profile that nothing selects. Only the test trees are asked the
 * second question. A profile file under the main resources may be activated by a deployment that the
 * repository does not hold, so its silence proves nothing, while a test profile can only be reached from
 * Java and its silence is therefore complete.
 */
@UtilityClass
final class SpringTestRules {

    private static final Pattern ACTIVE_PROFILES = Pattern.compile("@ActiveProfiles\\s*\\(([^)]*)\\)");
    private static final Pattern DECLARED_PROFILE = Pattern.compile("@Profile\\s*\\(([^)]*)\\)");
    private static final Pattern LITERAL = Pattern.compile("\"([^\"]*)\"");
    private static final String NEGATION = "!";

    /**
     * Every profile a test activates that neither a configuration file nor a bean declaration answers.
     *
     * @param types      the module already read
     * @param configured the profiles a configuration file in this module is named after
     * @return one offence per unanswered profile, by source and line
     */
    static List<String> missingProfiles(SpringTypes types, Collection<String> configured) {
        Set<String> selectable = selectable(types, configured);
        return types.all().stream()
            .flatMap(type -> unmatched(type, selectable))
            .toList();
    }

    /*
     * A profile is answered either by a file named after it or by a bean that @Profile selects on. Reading
     * only the files would report the second as missing, which is the false positive that would make a
     * suite using profiles purely to pick beans unable to use this rule at all.
     */
    private static Set<String> selectable(SpringTypes types, Collection<String> configured) {
        return Stream.concat(
            configured.stream(),
            types.all().stream().flatMap(type -> declaredProfiles(type.quoted()))
        ).collect(Collectors.toSet());
    }

    private static Stream<String> declaredProfiles(String source) {
        return DECLARED_PROFILE.matcher(source).results()
            .flatMap(SpringTestRules::requested)
            .map(Requested::name);
    }

    /**
     * Every test profile file that no test activates and no bean is declared for.
     *
     * @param types  the module already read
     * @param tested the profiles the test resource trees carry a file for, by the path of that file
     * @return one offence per file, in the order the profiles sort
     */
    static List<String> unactivatedProfiles(SpringTypes types, Map<String, String> tested) {
        Set<String> named = types.all().stream()
            .flatMap(SpringTestRules::selected)
            .collect(Collectors.toSet());
        return tested.entrySet().stream()
            .filter(profile -> !named.contains(profile.getKey()))
            .map(SpringTestRules::unactivated)
            .toList();
    }

    private static Stream<String> selected(SpringTypes.Declared type) {
        String source = type.quoted();
        return Stream.concat(
            ACTIVE_PROFILES.matcher(source).results().flatMap(SpringTestRules::requested).map(Requested::name),
            declaredProfiles(source)
        );
    }

    private static String unactivated(Map.Entry<String, String> profile) {
        return profile.getValue() + ": no test activates the " + profile.getKey()
            + " profile and no bean is declared for it, so every setting in this file is read by nothing;"
            + " Spring passes over a profile nobody selects without a word, which is what lets the file"
            + " sit here reading as though it were in force";
    }

    private static Stream<Requested> requested(MatchResult annotation) {
        return LITERAL.matcher(annotation.group(1)).results()
            .map(literal -> new Requested(without(literal.group(1).strip()), annotation.start()));
    }

    private static String without(String profile) {
        return profile.startsWith(NEGATION) ? profile.substring(NEGATION.length()) : profile;
    }

    private static Stream<String> unmatched(SpringTypes.Declared type, Collection<String> selectable) {
        return ACTIVE_PROFILES.matcher(type.quoted()).results()
            .flatMap(SpringTestRules::requested)
            .filter(profile -> !selectable.contains(profile.name()))
            .map(
                profile -> offence(
                    type, profile.at(),
                    "the profile " + profile.name() + " is activated by nothing: Spring accepts an"
                        + " unknown profile without a word, so the properties the test was written"
                        + " against are absent and it runs against the defaults while reading otherwise"
                )
            );
    }

    private static String offence(SpringTypes.Declared source, int at, String consequence) {
        return source.source() + ": line " + JavaCode.lineOf(source.text(), at) + ": " + consequence;
    }

    /**
     * One profile name as a test asked for it.
     *
     * @param name the profile, with any leading negation taken off
     * @param at   the offset the annotation opens at, which the offence is reported against
     */
    private record Requested(String name, int at) {
    }
}
