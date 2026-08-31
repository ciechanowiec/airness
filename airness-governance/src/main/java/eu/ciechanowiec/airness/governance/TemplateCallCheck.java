package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads every markup resource a module ships and reports the fragment calls that reach nothing it
 * declares.
 *
 * <p>The harness already holds a fragment declaration to the cap a callable is held to. Nothing reads
 * the other half. A fragment is called by name with a positional argument list, from a document that
 * does not declare it, and no part of a build says whether the name reaches anything or whether the
 * list is the length the declaration takes.
 *
 * <p>Nothing else says so either. The document parses, every analyzer passes over it, the page it
 * belongs to renders for as long as nothing draws that branch, and the first request that does fails
 * on a name. A fragment renamed in one file and called from four others is the ordinary way this
 * happens, and it is the class of defect a build is meant to reach before a reader does.
 *
 * <p>The repair is the name the module declares, which the offence carries, and the argument list that
 * declaration takes. A call handed too few arguments is not repaired by widening the declaration,
 * since every other caller then hands it too few as well.
 *
 * <p>What a call cannot be resolved to is passed over rather than guessed at. An expression that
 * builds the name it reaches, and a selector that reaches an element rather than a declaration, are
 * both left alone by {@link TemplateCallRules}, which is what keeps a layout mechanism that hands a
 * page its own body from reading as a call on nothing.
 */
public final class TemplateCallCheck {

    private static final String UNRESOLVED = "Fragment calls that reach nothing the module declares";

    private static final String MISCOUNTED
        = "Fragment calls handed an argument list the declaration does not take";

    private final MarkupScan scan;

    private final TemplateIndex index;

    /**
     * Creates a check over the markup one module ships.
     *
     * @param root          repository root the offences are reported relative to
     * @param resourceRoots resource directories of the module
     */
    public TemplateCallCheck(Path root, Collection<Path> resourceRoots) {
        this.scan = new MarkupScan(root, resourceRoots);
        this.index = new TemplateIndex(root, resourceRoots);
    }

    /**
     * How many markup resources the check read.
     *
     * @return the number of files in scope
     */
    public int scanned() {
        return this.scan.scanned();
    }

    /**
     * The two call rules and every expression that breaks one of them.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        List<Offence> found = this.scan.gathered(
            (named, offences) -> new Calls(named, offences, this.index)
        );
        return List.of(
            new Findings(UNRESOLVED, lines(found, Rule.UNRESOLVED)),
            new Findings(MISCOUNTED, lines(found, Rule.MISCOUNTED))
        );
    }

    private static List<String> lines(Collection<Offence> found, Rule rule) {
        return found.stream().filter(offence -> offence.rule() == rule).map(Offence::line).toList();
    }

    /**
     * Which of the two rules an offence was found against.
     */
    private enum Rule {

        /**
         * A call naming a template or a fragment the module declares nowhere.
         */
        UNRESOLVED,

        /**
         * A call whose argument list is not the length the declaration takes.
         */
        MISCOUNTED
    }

    /**
     * One offence, kept with the rule it was found against so that both verdicts come from one reading.
     *
     * @param rule which rule the call broke
     * @param line where it was written and what it did
     */
    private record Offence(Rule rule, String line) {
    }

    /**
     * The place something was written, carried as one value so that reporting it does not widen every
     * signature it passes through.
     *
     * @param line   the line it was written on
     * @param column the column it was written at
     */
    private record Placement(int line, int column) {
    }

    /**
     * Collects every fragment call the parser reaches and resolves it against what the module declares.
     */
    private static final class Calls implements MarkupElement {

        private final Path named;

        private final Collection<Offence> offences;

        private final TemplateIndex index;

        private Calls(Path named, Collection<Offence> offences, TemplateIndex index) {
            this.named = named;
            this.offences = offences;
            this.index = index;
        }

        @Override
        public void read(Map<String, String> attributes, int line, int column) {
            attributes.entrySet()
                .stream()
                .filter(attribute -> TemplateCallRules.caller(attribute.getKey()))
                .forEach(attribute -> this.resolve(attribute.getValue(), new Placement(line, column)));
        }

        private void resolve(String value, Placement placement) {
            TemplateCallRules.calls(value).forEach(call -> this.reach(call, placement));
        }

        private void reach(FragmentCall call, Placement placement) {
            Optional<Path> reached = call.local()
                ? Optional.of(this.named)
                : this.index.template(call.template());
            reached.ifPresentOrElse(
                document -> this.declared(call, document, placement),
                () -> this.add(
                    Rule.UNRESOLVED, placement,
                    "names the template %s, and no markup resource of this module answers it"
                        .formatted(call.template())
                )
            );
        }

        private void declared(FragmentCall call, Path document, Placement placement) {
            if (call.whole()) {
                return;
            }
            this.index.fragment(document, call.fragment()).ifPresentOrElse(
                arguments -> this.count(call, document, arguments, placement),
                () -> this.add(
                    Rule.UNRESOLVED, placement,
                    "names the fragment %s of %s, which declares no fragment of that name"
                        .formatted(call.fragment(), document)
                )
            );
        }

        private void count(FragmentCall call, Path document, int declared, Placement placement) {
            if (call.arguments() != declared) {
                this.add(
                    Rule.MISCOUNTED, placement,
                    "hands %s of %s %d argument(s), and it is declared to take %d"
                        .formatted(call.fragment(), document, call.arguments(), declared)
                );
            }
        }

        private void add(Rule rule, Placement placement, String said) {
            this.offences.add(
                new Offence(rule, "%s:%d:%d: %s".formatted(this.named, placement.line(), placement.column(), said))
            );
        }
    }
}
