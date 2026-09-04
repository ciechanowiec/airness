package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The files of one bundle: the same named texts written once per language, which a runtime chooses
 * between by the language of its reader.
 *
 * <p>A family is the directory a file sits in together with the name it carries before its language,
 * because two bundles of the same name in two directories are two bundles. A file whose name carries
 * no language is a family of one, which is what a project holding a single language has and what lets
 * such a project meet this rule without doing anything.
 *
 * <p>A suffix counts as a language when the runtime would read it as one, which is what decides the
 * behaviour this check exists to protect. A file named for a summary rather than for a tongue keeps
 * its whole name, so nothing pairs it with a bundle it has no relation to.
 *
 * @param named the directory and the name before the language, which is what makes two files one
 *              bundle
 * @param files the files of that bundle, in the order they were found
 */
record BundleFamily(String named, List<Path> files) {

    private static final Set<String> LANGUAGES = Set.of(Locale.getISOLanguages());
    private static final String SUFFIX = ".properties";
    private static final char LANGUAGE = '_';

    /**
     * Copies the files, so a caller that keeps its own cannot alter a family already reported.
     *
     * @param named the directory and the name before the language
     * @param files the files of that bundle
     */
    BundleFamily {
        files = List.copyOf(files);
    }

    /**
     * Gathers the given bundles into families, keeping the order they arrived in.
     *
     * @param resources every bundle file of the module
     * @return one family per bundle, each holding the languages that bundle is written in
     */
    static List<BundleFamily> of(Collection<Path> resources) {
        Map<String, List<Path>> gathered = new LinkedHashMap<>();
        resources.forEach(file -> gathered.computeIfAbsent(naming(file), _ -> new ArrayList<>()).add(file));
        return gathered.entrySet()
            .stream()
            .map(family -> new BundleFamily(family.getKey(), family.getValue()))
            .toList();
    }

    // The directory and the name before the language, which is what two files of one bundle share and
    // what two bundles of the same name in two directories do not. It is built as a path beside the
    // file rather than out of the parent, because a file at the root of a tree has no parent to read
    // and answering that with a branch would be answering a case this never meets.
    private static String naming(Path file) {
        return file.resolveSibling(base(file.getFileName().toString())).toString();
    }

    // The name a bundle carries before its language. The first segment that the runtime would read as
    // a language ends the name, so a bundle of one language and its base file answer the same thing.
    private static String base(String written) {
        String bare = written.substring(0, written.length() - SUFFIX.length());
        int at = bare.indexOf(LANGUAGE);
        while (at >= 0) {
            if (LANGUAGES.contains(segment(bare, at))) {
                return bare.substring(0, at);
            }
            at = bare.indexOf(LANGUAGE, at + 1);
        }
        return bare;
    }

    private static String segment(String bare, int at) {
        int next = bare.indexOf(LANGUAGE, at + 1);
        return next < 0 ? bare.substring(at + 1) : bare.substring(at + 1, next);
    }
}
