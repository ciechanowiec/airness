package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads every markup resource a module ships and reports the markup a layout throws away unread.
 *
 * <p>A page drawn in a shared layout replaces itself with that layout and hands over the parts of
 * itself the layout is to draw, each named by a selector. The layout draws those parts. Everything
 * else the page wrote is discarded, because the element carrying the replacement is the whole
 * document and nothing outside what was handed over survives it.
 *
 * <p>Nothing else in a build says so. The document parses, every expression in it resolves, the page
 * renders, and what was discarded is missing from it. A test over the rendered page asserts what came
 * back rather than what ought to have, so markup nobody receives reads exactly like markup nobody
 * asked about, and a suite of any size passes with an element of the page absent from every answer it
 * ever gave.
 *
 * <p>Three things save an element from being reported, and each of them is a way of being reached.
 * The element is handed over, or holds something that is. It declares a fragment, which is reached by
 * name from wherever that name is written rather than by any selector here. Or it holds such a
 * declaration, which is what a table wrapping a row of one is for: the wrapper is never drawn and the
 * rows are, and the wrapper exists so that the rows are valid markup.
 *
 * <p>A document is passed over whole where it hands over something this cannot resolve. An identifier
 * names one element, while a class or a tag names however many carry it, and a rule that reported
 * against a set of handed-over parts it knows to be incomplete would report correct markup. Erring
 * toward saying nothing is the only safe direction for a rule about reach.
 */
public final class TemplateReachCheck {

    private static final String HEADLINE = "Markup no fragment expression reaches, which a layout discards unread";

    private final MarkupScan scan;

    /**
     * Creates a check over the markup one module ships.
     *
     * @param root          repository root the offences are reported relative to
     * @param resourceRoots resource directories of the module
     */
    public TemplateReachCheck(Path root, Collection<Path> resourceRoots) {
        this.scan = new MarkupScan(root, resourceRoots);
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
     * What the check found, as one verdict.
     *
     * @return the verdict, holding no offence where every document keeps what it wrote
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.scan.offences(Reach::new)));
    }

    /**
     * Reads one document and reports the parts of its body that nothing reaches.
     *
     * <p>What it keeps as it walks is a stack of the elements it has open. Depth is then the size of
     * that stack and needs no counter of its own, where the element a part of the body sits under is
     * whatever the stack is holding, so a part of the body is an element opened while the body itself
     * is the one directly above it. Nothing here is a field that changes, which is the shape this
     * repository holds every class to and which a reading of a document has no trouble keeping.
     */
    private static final class Reach implements MarkupElement {

        private static final String BODY = "body";

        private static final String IDENTIFIER = "id";

        private static final String NAMES = "#";

        private static final Set<String> FRAGMENTS = Set.of("th:fragment", "data-th-fragment");

        private static final Set<String> REPLACEMENTS = Set.of(
            "th:replace", "th:insert", "data-th-replace", "data-th-insert"
        );

        private final Path named;

        private final Collection<String> offences;

        private final Deque<String> open;

        private final Set<String> handed;

        private final Discarded discarded;

        private Reach(Path named, Collection<String> offences) {
            this.named = named;
            this.offences = offences;
            this.open = new ArrayDeque<>();
            this.handed = new HashSet<>();
            this.discarded = new Discarded();
        }

        @Override
        public void read(Map<String, String> attributes, int line, int column) {
            Map<String, String> carried = lowered(attributes);
            if (this.open.size() == 1) {
                this.handing(carried);
            }
            if (this.applies() && this.open.contains(BODY)) {
                this.discarded.note(carried);
            }
        }

        @Override
        public void opened(String element, int line, int column) {
            if (this.applies() && BODY.equals(this.open.peek())) {
                this.discarded.begin(element, line, column);
            }
            this.open.push(name(element));
        }

        @Override
        public void closed(String element) {
            this.open.poll();
            if (this.applies() && BODY.equals(this.open.peek())) {
                this.discarded.end(this.handed).map(this::worded).ifPresent(this.offences::add);
            }
        }

        // Whether this document is one this rule can answer for, which is the same question as whether
        // anything was handed over that can be named. A document replacing itself with nothing hands
        // over nothing, and one handing over a class or a tag hands over more elements than a name
        // reaches, so neither fills this and neither is read further.
        private boolean applies() {
            return !this.handed.isEmpty();
        }

        // What the root of the document hands a layout. Nothing is kept unless every part of it can be
        // named, because a set of handed-over parts known to be incomplete would report markup that is
        // reached by something this could not read.
        private void handing(Map<String, String> carried) {
            List<String> reached = REPLACEMENTS.stream().map(carried::get).filter(Objects::nonNull)
                .flatMap(value -> TemplateCallRules.selectors(value).stream()).toList();
            boolean nameable = !reached.isEmpty() && reached.stream().allMatch(one -> one.startsWith(NAMES));
            if (nameable) {
                reached.stream().map(one -> one.substring(NAMES.length())).forEach(this.handed::add);
            }
        }

        private String worded(Part part) {
            return "%s:%d:%d: <%s> is handed to no fragment expression, so the layout discards it. "
                .formatted(this.named, part.line(), part.column(), part.element())
                + "Put it inside one the layout is given, or declare it as a fragment";
        }

        private static Map<String, String> lowered(Map<String, String> attributes) {
            return attributes.entrySet().stream().collect(
                Collectors.toMap(
                    entry -> name(entry.getKey()), Map.Entry::getValue, (first, _) -> first, LinkedHashMap::new
                )
            );
        }

        private static String name(String written) {
            return written.toLowerCase(Locale.ROOT);
        }

        /**
         * One element of a body, and where it was written.
         *
         * @param element the name of the element, as the document spells it
         * @param line    the line it was written on
         * @param column  the column it was written at
         */
        private record Part(String element, int line, int column) {
        }

        /**
         * The one part of a body currently open, and everything read inside it so far.
         */
        private static final class Discarded {

            private final Deque<Part> open;

            private final Set<String> identifiers;

            private final Set<String> declarations;

            private Discarded() {
                this.open = new ArrayDeque<>();
                this.identifiers = new HashSet<>();
                this.declarations = new HashSet<>();
            }

            private void begin(String element, int line, int column) {
                this.forget();
                this.open.push(new Part(element, line, column));
            }

            private void note(Map<String, String> carried) {
                Optional.ofNullable(carried.get(IDENTIFIER)).ifPresent(this.identifiers::add);
                FRAGMENTS.stream().filter(carried::containsKey).forEach(this.declarations::add);
            }

            private Optional<Part> end(Set<String> handed) {
                Optional<Part> reported = Optional.ofNullable(this.open.poll())
                    .filter(_ -> this.declarations.isEmpty())
                    .filter(_ -> Collections.disjoint(this.identifiers, handed));
                this.forget();
                return reported;
            }

            private void forget() {
                this.open.clear();
                this.identifiers.clear();
                this.declarations.clear();
            }
        }
    }
}
