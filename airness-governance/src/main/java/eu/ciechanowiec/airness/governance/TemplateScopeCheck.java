package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads every markup resource a module ships and reports a fragment that reads a name bound outside
 * it.
 *
 * <p>A fragment is asked for on its own whenever a page replaces part of itself without being drawn
 * again, which is the ordinary way a list turns a page and a filter narrows one. The engine answers
 * such a request by processing the element the fragment is declared on and everything under it, and
 * nothing above it. An attribute that binds a name therefore runs when the whole page is drawn and
 * does not run when the fragment alone is, so the name is bound the first time and unbound every time
 * after.
 *
 * <p>Nothing else in a build says so, and the silence is total. An unbound name is not an error to the
 * engine: a condition reading one decides against, a value writing one writes nothing, and the request
 * is answered with markup that parses and renders. A suite reads the whole page, where the binding did
 * run, so every test passes while the second page of a list is served without the controls the first
 * one had.
 *
 * <p>A name bound again at the fragment or inside it is read rather than reported, because that
 * binding runs whenever the fragment does. That is also the repair, and it is one line: bind the name
 * on the fragment as well as above it. A fragment that declares the name as a parameter is left alone
 * for the same reason, since what a caller hands over arrives however the fragment was reached.
 */
public final class TemplateScopeCheck {

    private static final String HEADLINE
        = "Fragments reading a name bound outside them, which a request for the fragment alone leaves unbound";

    private final MarkupScan scan;

    /**
     * Creates a check over the markup one module ships.
     *
     * @param root          repository root the offences are reported relative to
     * @param resourceRoots resource directories of the module
     */
    public TemplateScopeCheck(Path root, Collection<Path> resourceRoots) {
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
     * @return the verdict, holding no offence where every fragment binds what it reads
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.scan.offences(Unbound::new)));
    }

    /**
     * Reads one document and reports every name a fragment reads from above itself.
     *
     * <p>What it keeps as it walks is a stack of the names each open element binds, and a stack of the
     * depths the open fragments sit at. Where a name was bound is then which frame holds it, and
     * whether that frame is inside the fragment is a comparison of two numbers. Nothing here is a
     * field that changes, which is the shape this repository holds every class to.
     */
    private static final class Unbound implements MarkupElement {

        private static final String THYMELEAF = "th:";

        // The spelling a document uses when it has to stay valid HTML5, which no dialect prefix is.
        private static final String THYMELEAF_DATA = "data-th-";

        // An expression written in the text between two elements rather than on an element, under both
        // the escaping mark and the one that writes markup out as it stands.
        private static final Pattern INLINED = Pattern.compile("\\[\\[(.*?)]]|\\[\\((.*?)\\)]", Pattern.DOTALL);

        private final Path named;

        private final Collection<String> offences;

        private final Deque<Set<String>> bound;

        private final Deque<Integer> fragments;

        private Unbound(Path named, Collection<String> offences) {
            this.named = named;
            this.offences = offences;
            this.bound = new ArrayDeque<>();
            this.fragments = new ArrayDeque<>();
        }

        @Override
        public void read(Map<String, String> attributes, int line, int column) {
            Map<String, String> carried = lowered(attributes);
            Optional.ofNullable(this.bound.peek()).ifPresent(here -> this.binds(here, carried));
            this.reading(carried, line, column);
        }

        @Override
        public void text(String content, int line, int column) {
            Matcher marked = INLINED.matcher(content);
            Collection<String> written = new HashSet<>();
            while (marked.find()) {
                written.add(Optional.ofNullable(marked.group(1)).orElseGet(() -> marked.group(2)));
            }
            this.inspecting(written, line, column);
        }

        @Override
        public void opened(String element, int line, int column) {
            this.bound.push(new HashSet<>());
        }

        @Override
        public void closed(String element) {
            this.bound.poll();
            Optional.ofNullable(this.fragments.peek())
                .filter(depth -> depth > this.bound.size())
                .ifPresent(_ -> this.fragments.poll());
        }

        // What this element binds into the markup under it, and whether it declares a fragment. A
        // fragment's own parameters are bindings of its own, because what a caller hands over arrives
        // however the fragment was reached.
        private void binds(Set<String> here, Map<String, String> carried) {
            here.addAll(TemplateScopeRules.bound(carried));
            carried.entrySet()
                .stream()
                .filter(entry -> TemplateFragmentCheck.declares(entry.getKey()))
                .findFirst()
                .ifPresent(entry -> this.declares(here, entry.getValue()));
        }

        private void declares(Set<String> here, String written) {
            here.addAll(FragmentSignature.names(written));
            this.fragments.push(this.bound.size());
        }

        // Every value of this element that the engine evaluates, read against the names bound above the
        // fragment. A fragment declaration is passed over, because what it carries is a list of
        // parameter names rather than an expression.
        private void reading(Map<String, String> carried, int line, int column) {
            this.inspecting(
                carried.entrySet()
                    .stream()
                    .filter(entry -> evaluated(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toList(),
                line, column
            );
        }

        private void inspecting(Collection<String> values, int line, int column) {
            Optional.ofNullable(this.fragments.peek())
                .map(this::reachable)
                .ifPresent(names -> this.report(names, values, line, column));
        }

        private void report(Collection<String> names, Collection<String> values, int line, int column) {
            values.stream()
                .flatMap(value -> names.stream().filter(name -> TemplateScopeRules.reads(value, name)))
                .distinct()
                .forEach(name -> this.offences.add(this.worded(name, line, column)));
        }

        // The names bound above the innermost open fragment and nowhere at or inside it. The frames
        // are held innermost first, so the ones at and inside the fragment are the first of them, and
        // everything after those is what a request for the fragment alone would never bind.
        private Set<String> reachable(int depth) {
            long held = this.bound.size() - depth + 1L;
            Set<String> above = this.bound.stream()
                .skip(held)
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(HashSet::new));
            this.bound.stream().limit(held).flatMap(Set::stream).forEach(above::remove);
            return above;
        }

        private String worded(String name, int line, int column) {
            return "%s:%d:%d: the fragment reads %s, which is bound above it, so a request for the fragment alone "
                .formatted(this.named, line, column, name)
                + "leaves it unbound and the markup it decides is silently missing. Bind it on the fragment as well";
        }

        // Whether the engine evaluates what this attribute carries. A fragment declaration is the one
        // dialect attribute that carries names rather than an expression.
        private static boolean evaluated(String attribute) {
            boolean dialect = attribute.startsWith(THYMELEAF) || attribute.startsWith(THYMELEAF_DATA);
            return dialect && !TemplateFragmentCheck.declares(attribute);
        }

        private static Map<String, String> lowered(Map<String, String> attributes) {
            return attributes.entrySet().stream().collect(
                Collectors.toMap(
                    entry -> entry.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue,
                    (first, _) -> first, LinkedHashMap::new
                )
            );
        }
    }
}
