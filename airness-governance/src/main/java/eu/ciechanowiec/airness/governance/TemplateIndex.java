package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Every fragment the markup of one module declares, read once so that a rule can judge one document
 * against the others.
 *
 * <p>This is to markup what {@link SpringTypes} is to Java, and it exists for the same reason. A
 * fragment is declared in one document and called from another, so the question of whether a call
 * reaches anything cannot be answered from the document the call was written in. Reading every
 * document before judging any is the only shape in which that question can be asked.
 *
 * <p>A template is found by the tail of its path rather than by a directory this code names. The
 * engine resolves a view name against a prefix the project may set, and a check that assumed the
 * usual prefix would report every call in a project that set another one, which is a worse answer
 * than a missed name. Matching the tail resolves a name under any prefix, at the cost of accepting a
 * name that is a tail of some other template, and that trade is the right way round: a rule that
 * fails a correct project is one nobody can adopt.
 *
 * <p>A document that declares no fragment is still a template, so the documents are taken from the
 * scan rather than from what the reading gathered. A view name reaches a whole page far more often
 * than it reaches a fragment of one.
 *
 * <p>What the markup calls is held here beside what it declares, by name alone rather than by the
 * document a name resolves to. A call naming a fragment nothing declares is already reported by
 * {@link TemplateCallCheck}, so a name held here either reaches a declaration or is an offence made
 * elsewhere, and resolving the template half a second time would give one question two answers. Reading
 * the name alone is also what makes a call on the document that wrote it need no handling of its own.
 */
final class TemplateIndex {

    private static final String SUFFIX = ".html";

    private static final String SEPARATOR = "/";

    private final MarkupScan scan;

    private final List<Path> documents;

    private final List<FragmentDeclaration> found;

    private final Map<Path, Map<String, Integer>> declared;

    private final Set<String> called;

    /**
     * Reads the markup of one module and records the fragments it declares.
     *
     * @param root          repository root the documents are named relative to
     * @param resourceRoots resource directories of the module
     */
    TemplateIndex(Path root, Collection<Path> resourceRoots) {
        this.scan = new MarkupScan(root, resourceRoots);
        this.documents = this.scan.files();
        this.found = this.scan.gathered(Declarations::new);
        this.declared = byDocument(this.found);
        this.called = calls(this.scan);
    }

    /**
     * How many markup resources the index read.
     *
     * @return the number of documents in scope
     */
    int scanned() {
        return this.scan.scanned();
    }

    /**
     * Every document the index holds, which is every markup resource the module ships.
     *
     * @return the documents, relative to the repository root
     */
    List<Path> documents() {
        return this.documents;
    }

    /**
     * Every fragment declaration the markup of the module writes, and where each was written.
     *
     * <p>A document declaring one name twice contributes both, unlike {@link #fragment(Path, String)},
     * which keeps the first because that is the one the engine finds. A rule reporting on a declaration
     * reports on each place one was written, since each is a place a reader has to go.
     *
     * @return the declarations, in the order the documents were read
     */
    List<FragmentDeclaration> declarations() {
        return this.found;
    }

    /**
     * Whether any fragment call the markup writes names the given fragment.
     *
     * @param fragment the fragment name
     * @return whether a call writes that name out
     */
    boolean called(String fragment) {
        return this.called.contains(fragment.trim());
    }

    /**
     * The document a template name reaches.
     *
     * @param name the template name as an expression or a view name wrote it
     * @return the document it names, and nothing when no document answers it
     */
    Optional<Path> template(String name) {
        String wanted = trimmed(name);
        return wanted.isEmpty() ? Optional.empty()
            : this.documents.stream().filter(document -> answers(document, wanted)).findFirst();
    }

    /**
     * How many arguments a fragment of the given document declares.
     *
     * @param document the document the fragment would be declared in
     * @param name     the fragment name
     * @return the number of arguments it declares, and nothing when it declares no such fragment
     */
    Optional<Integer> fragment(Path document, String name) {
        return Optional.ofNullable(this.declared.get(document))
            .map(fragments -> fragments.get(name.trim()));
    }

    // Whether a document is the one a template name reaches, which is so when the name is the tail of
    // its path once the suffix the engine adds is taken off again. Every document carries that suffix,
    // since what is markup at all is decided before this is asked, so taking it off needs no guard.
    private static boolean answers(Path document, String wanted) {
        String written = document.toString().replace(document.getFileSystem().getSeparator(), SEPARATOR);
        String stem = written.substring(0, written.length() - SUFFIX.length());
        return stem.equals(wanted) || stem.endsWith(SEPARATOR + wanted);
    }

    // A name may arrive quoted, because a view name is written as a Java literal and a template name is
    // written as a Thymeleaf one. Neither quotation is part of the name.
    private static String trimmed(String name) {
        String bare = name.trim();
        return quoted(bare, '\'') || quoted(bare, '"') ? bare.substring(1, bare.length() - 1).trim() : bare;
    }

    private static boolean quoted(String bare, char mark) {
        return bare.length() > 1 && bare.charAt(0) == mark && bare.charAt(bare.length() - 1) == mark;
    }

    // A document declaring the same name twice keeps the first, which is the one the engine finds.
    private static Map<Path, Map<String, Integer>> byDocument(Collection<FragmentDeclaration> found) {
        return found.stream().collect(
            Collectors.groupingBy(
                FragmentDeclaration::in,
                Collectors.toMap(FragmentDeclaration::fragment, FragmentDeclaration::arguments, (first, _) -> first)
            )
        );
    }

    // What a call names is read by the rules over calls, which already drop a name an expression builds,
    // a markup selector, and a call reaching a whole template rather than a declaration inside one.
    private static Set<String> calls(MarkupScan scan) {
        return new TreeSet<>(scan.gathered((_, kept) -> new Calls(kept)));
    }

    /**
     * Collects every fragment declaration the parser reaches.
     */
    private static final class Declarations implements MarkupElement {

        private final Path named;

        private final Collection<FragmentDeclaration> found;

        private Declarations(Path named, Collection<FragmentDeclaration> found) {
            this.named = named;
            this.found = found;
        }

        @Override
        public void read(Map<String, String> attributes, int line, int column) {
            attributes.entrySet()
                .stream()
                .filter(attribute -> TemplateFragmentCheck.declares(attribute.getKey()))
                .forEach(attribute -> this.keep(attribute.getValue(), line, column));
        }

        private void keep(String declaration, int line, int column) {
            this.found.add(
                new FragmentDeclaration(
                    this.named,
                    FragmentSignature.name(declaration),
                    FragmentSignature.arguments(declaration),
                    line,
                    column
                )
            );
        }
    }

    /**
     * Collects the fragment name every call the parser reaches writes out.
     */
    private static final class Calls implements MarkupElement {

        private final Collection<String> found;

        private Calls(Collection<String> found) {
            this.found = found;
        }

        @Override
        public void read(Map<String, String> attributes, int line, int column) {
            attributes.entrySet()
                .stream()
                .filter(attribute -> TemplateCallRules.caller(attribute.getKey()))
                .flatMap(attribute -> TemplateCallRules.reached(attribute.getValue()).stream())
                .forEach(this.found::add);
        }
    }
}
