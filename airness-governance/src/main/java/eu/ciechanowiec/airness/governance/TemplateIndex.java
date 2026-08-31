package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 */
final class TemplateIndex {

    private static final String SUFFIX = ".html";

    private static final String SEPARATOR = "/";

    private final MarkupScan scan;

    private final List<Path> documents;

    private final Map<Path, Map<String, Integer>> declared;

    /**
     * Reads the markup of one module and records the fragments it declares.
     *
     * @param root          repository root the documents are named relative to
     * @param resourceRoots resource directories of the module
     */
    TemplateIndex(Path root, Collection<Path> resourceRoots) {
        this.scan = new MarkupScan(root, resourceRoots);
        this.documents = this.scan.files();
        this.declared = declarations(this.scan);
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
    private static Map<Path, Map<String, Integer>> declarations(MarkupScan scan) {
        return scan.gathered(Declarations::new).stream().collect(
            Collectors.groupingBy(
                Declaration::in,
                Collectors.toMap(Declaration::fragment, Declaration::arguments, (first, _) -> first)
            )
        );
    }

    /**
     * One fragment declaration, and the document that declared it.
     *
     * @param in        the document the declaration was written in
     * @param fragment  the name the fragment is called by
     * @param arguments how many arguments it declares
     */
    private record Declaration(Path in, String fragment, int arguments) {
    }

    /**
     * Collects every fragment declaration the parser reaches.
     */
    private static final class Declarations implements MarkupElement {

        private final Path named;

        private final Collection<Declaration> found;

        private Declarations(Path named, Collection<Declaration> found) {
            this.named = named;
            this.found = found;
        }

        @Override
        public void read(Map<String, String> attributes, int line, int column) {
            attributes.entrySet()
                .stream()
                .filter(attribute -> TemplateFragmentCheck.declares(attribute.getKey()))
                .forEach(attribute -> this.keep(attribute.getValue()));
        }

        private void keep(String declaration) {
            this.found.add(
                new Declaration(
                    this.named,
                    FragmentSignature.name(declaration),
                    FragmentSignature.arguments(declaration)
                )
            );
        }
    }
}
