package eu.ciechanowiec.airness.governance;

import eu.ciechanowiec.airness.governance.MessageBundle.Declaration;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every language of a message bundle declares the same names, and declares each of them once.
 *
 * <p>Both defects are silent, which is the whole reason a check reads them. A name one language omits
 * is answered out of another rather than reported, so a reader who asked for one language is handed a
 * sentence of a different one and nothing anywhere says so. A name declared twice in one file is read
 * into one value, so the earlier of the two is replaced by the later and neither the build nor the
 * runtime mentions it.
 *
 * <p>Neither is reachable by any other rule of this harness. A bundle is not source, so nothing
 * compiles it. It is not markup, so no parser reads it. What it declares is looked up by name at run
 * time, so no reference to it is resolved before then.
 *
 * <p>Parity is read in both directions. A name a translation adds and the language the project writes
 * in has since dropped is the same defect wearing the other face: it is dead in one file and missing
 * from the rest, and reading only one direction would leave it there.
 *
 * <p>What is passed over is a name nothing reaches. A name is composed as readily in a program as it
 * is written out, so a name in use can be one no search finds, and reporting it as unused would refuse
 * correct work. Parity needs nothing of the sort: it compares one file against another.
 */
public final class MessageParityCheck {

    private static final String PARITY = "Message bundle names one language declares and another omits";
    private static final String REPEATED = "Message bundle names declared more than once in one file";

    private final List<Path> resources;
    private final List<String> divergences;
    private final List<String> repeats;

    /**
     * Reads every bundle of the module once, so both rules are answered from one pass.
     *
     * @param root          the working tree root
     * @param resourceRoots resource directories of the module
     */
    public MessageParityCheck(Path root, Collection<Path> resourceRoots) {
        this.resources = MessageResources.of(root, resourceRoots);
        Map<Path, MessageBundle> read = read(this.resources);
        this.divergences = divergences(root, read);
        this.repeats = repeats(root, read);
    }

    /**
     * How many bundle files the check read, which a caller logs so the reach of a clean verdict is on
     * the record rather than in someone's memory.
     *
     * @return the number of bundles read
     */
    public int scanned() {
        return this.resources.size();
    }

    /**
     * The names one language omits, and the names one file declares twice.
     *
     * @return one verdict per rule
     */
    public List<Findings> findings() {
        return List.of(
            new Findings(PARITY, this.divergences),
            new Findings(REPEATED, this.repeats)
        );
    }

    private static Map<Path, MessageBundle> read(Collection<Path> resources) {
        Map<Path, MessageBundle> bundles = new LinkedHashMap<>();
        resources.forEach(
            file -> Repository.readText(file).ifPresent(content -> bundles.put(file, new MessageBundle(content)))
        );
        return bundles;
    }

    private static List<String> divergences(Path root, Map<Path, MessageBundle> read) {
        Map<Path, Set<String>> named = named(read);
        return BundleFamily.of(read.keySet())
            .stream()
            .filter(family -> family.files().size() > 1)
            .flatMap(family -> omissions(root, family, named))
            .sorted()
            .toList();
    }

    // What every language of the bundle declares between them, held against what each one declares on
    // its own. A name the whole family carries is named by nobody, and one that any of them lacks is
    // named against that file and beside a language that does declare it.
    private static Stream<String> omissions(Path root, BundleFamily family, Map<Path, Set<String>> named) {
        Set<String> together = family.files()
            .stream()
            .flatMap(file -> names(named, file).stream())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return family.files()
            .stream()
            .flatMap(
                file -> together.stream()
                    .filter(name -> !names(named, file).contains(name))
                    .map(
                        name -> ("%s: %s is declared by %s and not here. Declare it here, or remove it from "
                            + "every language of the bundle")
                            .formatted(relative(root, file), name, declaring(family, named, name))
                    )
            );
    }

    // Which language of the bundle does declare the name, so the reader is sent to the file the text
    // already exists in rather than left to find it.
    private static String declaring(BundleFamily family, Map<Path, Set<String>> named, String name) {
        return family.files()
            .stream()
            .filter(file -> names(named, file).contains(name))
            .map(file -> file.getFileName().toString())
            .sorted()
            .findFirst()
            .orElseThrow();
    }

    private static List<String> repeats(Path root, Map<Path, MessageBundle> read) {
        return read.entrySet()
            .stream()
            .flatMap(bundle -> repeated(root, bundle.getKey(), bundle.getValue()))
            .toList();
    }

    // Every declaration after the first of one name. The first is where the name belongs and the rest
    // are what the file loses, so the report names the ones that are lost.
    private static Stream<String> repeated(Path root, Path file, MessageBundle bundle) {
        return bundle.declarations()
            .stream()
            .collect(Collectors.groupingBy(Declaration::name, LinkedHashMap::new, Collectors.toList()))
            .values()
            .stream()
            .flatMap(declarations -> declarations.stream().skip(1))
            .sorted(Comparator.comparingInt(Declaration::line))
            .map(
                declaration -> ("%s:%d: %s is declared again here. Remove one of the two, because the file is "
                    + "read into one value per name")
                    .formatted(relative(root, file), declaration.line(), declaration.name())
            );
    }

    private static Map<Path, Set<String>> named(Map<Path, MessageBundle> read) {
        return read.entrySet()
            .stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    bundle -> bundle.getValue()
                        .declarations()
                        .stream()
                        .map(Declaration::name)
                        .collect(Collectors.toSet())
                )
            );
    }

    // A file the check read is always in the map, and answering an empty set rather than nothing keeps
    // that fact from being a claim the caller has to make on every lookup.
    private static Set<String> names(Map<Path, Set<String>> named, Path file) {
        return named.getOrDefault(file, Set.of());
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString();
    }
}
