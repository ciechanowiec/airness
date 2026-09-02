package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The types a set of sources declares, read once so that a rule can judge one file against the others.
 *
 * <p>Every Spring rule written so far answers about the file in front of it. A whole class of defect is
 * invisible from there, because the two halves are in different files: an entity is declared in one and
 * returned from a controller in another, a component is annotated in one and instantiated with
 * {@code new} in another, an application class is unremarkable until a second one exists somewhere else.
 * Reading every source before judging any is the only shape in which those questions can be asked, and
 * it is the shape {@link PackageGraph} already established for package cycles.
 *
 * <p>An annotation is credited to the source that carries it rather than to a particular declaration in
 * it. Airness allows one top-level type per file, so the two are the same thing here, and asking the
 * cheaper question keeps this reading text rather than parsing it.
 */
final class SpringTypes {

    private static final Pattern DECLARATION = Pattern.compile(
        "\\b(?:class|record|interface|enum)\\s+(\\w+)"
    );

    private final List<Declared> declared;

    private SpringTypes(Collection<Declared> declared) {
        this.declared = List.copyOf(declared);
    }

    /**
     * Reads every source and records the type it declares.
     *
     * @param root    the repository root every offence names a source relative to
     * @param sources the Java sources to read
     * @return the types they declare
     */
    static SpringTypes over(Path root, Collection<Path> sources) {
        return new SpringTypes(
            sources.stream().flatMap(source -> read(root, source).stream()).toList()
        );
    }

    /**
     * How many types were declared, which a caller reports so an empty scope reads as one.
     *
     * @return the number of declared types
     */
    int size() {
        return this.declared.size();
    }

    /**
     * Every source read, whatever it declares.
     *
     * <p>A rule that looks for a construct rather than for an annotation has to read the whole module,
     * because the file it reports is chosen by what it holds rather than by what it is marked as. That
     * is the difference between asking which types are components and asking which sources build one.
     *
     * @return the declared types, in the order the sources were given
     */
    List<Declared> all() {
        return this.declared;
    }

    /**
     * Every source carrying the given annotation.
     *
     * @param annotation the annotation to look for
     * @return the matching types, in the order the sources were given
     */
    List<Declared> carrying(Pattern annotation) {
        return this.declared.stream()
            .filter(type -> annotation.matcher(type.code()).find())
            .toList();
    }

    /**
     * The names of the types carrying the given annotation.
     *
     * @param annotation the annotation to look for
     * @return the type names, in order
     */
    Set<String> named(Pattern annotation) {
        return this.carrying(annotation).stream()
            .map(Declared::name)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Optional<Declared> read(Path root, Path source) {
        Optional<CharSequence> text = Repository.readText(source).map(CharSequence.class::cast);
        return text.flatMap(read -> declaredIn(root.relativize(source), read));
    }

    private static Optional<Declared> declaredIn(Path source, CharSequence text) {
        String code = JavaCode.blanked(text);
        Matcher found = DECLARATION.matcher(code);
        return found.find()
            ? Optional.of(new Declared(source, found.group(1), code, text))
            : Optional.empty();
    }

    /**
     * One source, by the type it declares and by what it holds.
     *
     * @param source the path relative to the repository root, which an offence names
     * @param name   the first type the source declares
     * @param code   the source with comments and literals blanked, which every rule reads
     * @param text   the source as written, which a line number is counted over
     */
    public record Declared(Path source, String name, String code, CharSequence text) {

        private static final String TESTS = "test";

        /**
         * The source with its comments gone and its one-line literals kept.
         *
         * <p>{@link #code()} is the right reading for structure and the wrong one for a rule that turns
         * on a value: blanking erases the very word that tells {@code @Scope("prototype")} from
         * {@code @Scope("request")}, and it takes the profile out of {@code @ActiveProfiles} and leaves
         * the annotation naming nothing. Offsets survive either blanking, so a line number taken here
         * is the line number of the file.
         *
         * @return the source carrying code and the literals written on one line
         */
        String quoted() {
            return JavaCode.withoutComments(this.text);
        }

        /**
         * Whether the source lies outside every test root.
         *
         * <p>The distinction decides two rules in opposite directions. Building a component with
         * {@code new} is how a test is meant to be written and is a defect anywhere else, while a
         * declared application context is only ever a test's to declare.
         *
         * @return whether no path element names the test tree
         */
        boolean production() {
            return IntStream.range(0, this.source.getNameCount())
                .noneMatch(element -> TESTS.equals(this.source.getName(element).toString()));
        }

        /**
         * This source named by the type it declares and the path it was read from.
         *
         * <p>The reading a record generates states every component, and two of these are whole files.
         * Nothing here asks to be printed, so what prints one is an accident: a map that rejects a
         * duplicate key reports the values it could not merge, and the report becomes a stack trace
         * carrying two sources inline. What identifies this source is its name and its path, and the
         * two fields holding a file are not an identity but the thing identified.
         *
         * @return the type this source declares and the path it sits at
         */
        @Override
        public String toString() {
            return "%s (%s)".formatted(this.name, this.source);
        }
    }
}
