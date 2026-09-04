package eu.ciechanowiec.airness.governance;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The fragments a module's markup declares, read against everything in the module that could reach one.
 *
 * <p>This is the mirror of {@link SpringViewRules}. That rule asks whether a name written in Java is
 * answered by the markup. This asks whether a fragment written in the markup is named by anything at
 * all, which is the half nothing else reads: a fragment nobody calls parses, is held to the argument
 * cap, passes the rule over calls because it is the callee rather than a caller, and goes on being
 * shipped and rendered by nothing for as long as anybody keeps editing it.
 *
 * <p>A fragment is reached in two ways here. The first is a fragment call in the module's own markup,
 * read by {@link TemplateCallRules}, which is the ordinary one. The second is the plain occurrence of
 * the name inside a string the module's Java writes, and that condition is deliberately cruder than
 * reading what a handler returns. A view name is as often assembled as returned, so a project answering
 * a list request with a call that joins a page constant to a fragment constant states the name in a
 * constant and nowhere else, and a rule reading returned literals alone would report every such
 * fragment. Measured over a project shipping forty-four fragments, the call condition alone reported
 * nine, seven of them wrong, and the two conditions together reported the two dead ones.
 *
 * <p>The trade is stated rather than hidden. Any string that so much as mentions the name exempts the
 * fragment, so a fragment named only by a log message, or by a constant nobody reads any more, is
 * missed. That is the direction this errs in on purpose. A false negative leaves a defect exactly where
 * it already was, while a false positive asks a project to delete markup its own handlers render, and a
 * rule that does the second once is turned off for good.
 *
 * <p>Evidence of reach is taken wherever it is written, test sources included, while the accusation is
 * made only about a declaration that exists. {@link SpringViewRules} reads production sources alone
 * because there a source is the accused. Here a source is the witness, and refusing a witness raises
 * false positives. A source declaring no type is not read at all, which costs nothing, because a
 * package declaration holds no view name.
 */
@UtilityClass
final class SpringFragmentRules {

    // A string literal written on one line, which is what the reading of a source keeps.
    private static final Pattern LITERAL = Pattern.compile("\"([^\"\\n]*)\"");

    // What parts one literal into words. A hyphen is part of a word here, because a fragment name may
    // carry one and splitting on it would leave item-row unmentioned by a literal that mentions it.
    private static final Pattern WORDS = Pattern.compile("[^\\w-]+");

    /**
     * Every fragment the module declares that nothing in the module reaches.
     *
     * @param types the module already read
     * @param index the markup the module ships
     * @return one offence per unreached declaration, by document and position
     */
    static List<String> unreachedFragments(SpringTypes types, TemplateIndex index) {
        Set<String> mentioned = mentioned(types);
        return index.declarations()
            .stream()
            .filter(declaration -> !index.called(declaration.fragment()))
            .filter(declaration -> !mentioned.contains(declaration.fragment()))
            .map(SpringFragmentRules::offence)
            .toList();
    }

    // Every word the module writes inside a string. Reading words rather than whole literals is what
    // lets a view name that joins a page to a fragment mention the fragment, while keeping a longer
    // word that merely opens with the name from mentioning it.
    private static Set<String> mentioned(SpringTypes types) {
        return types.all()
            .stream()
            .map(SpringTypes.Declared::quoted)
            .flatMap(read -> LITERAL.matcher(read).results())
            .flatMap(literal -> Arrays.stream(WORDS.split(literal.group(1))))
            .filter(word -> !word.isEmpty())
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String offence(FragmentDeclaration declaration) {
        return declaration.in() + ":" + declaration.line() + ":" + declaration.column()
            + ": declares the fragment " + declaration.fragment() + ", which no call in this module's"
            + " markup names and no string in its Java mentions, so nothing the module ships can render it";
    }
}
