package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import org.attoparser.ParseException;
import org.attoparser.config.ParseConfiguration;
import org.attoparser.simple.AbstractSimpleMarkupHandler;
import org.attoparser.simple.SimpleMarkupParser;
import org.jspecify.annotations.Nullable;

/**
 * Reads every markup resource a module ships and hands each element to whoever is asking about it.
 *
 * <p>More than one rule reads the same documents for different reasons, and everything they share is
 * here: which files are markup at all, how an element of one is reached, what an element carrying no
 * attribute arrives as, and what a document no engine could read contributes. A check that answered
 * any of those for itself would be a second answer to keep in step with this one.
 *
 * <p>What a check keeps is the only part that differs, which is what it makes of an element it is
 * handed. Most keep an offence, and one keeps what a document declares so that a later rule can ask
 * about it, so what is gathered is the calling check's own type rather than a line of text.
 */
final class MarkupScan {

    private final Path root;

    private final List<Path> files;

    /**
     * Creates a scan over the markup one module ships.
     *
     * @param root          repository root the offences are reported relative to
     * @param resourceRoots resource directories of the module
     */
    MarkupScan(Path root, Collection<Path> resourceRoots) {
        this.root = root;
        this.files = MarkupResources.of(root, resourceRoots);
    }

    /**
     * How many markup resources the scan read.
     *
     * @return the number of files in scope
     */
    int scanned() {
        return this.files.size();
    }

    /**
     * Every markup resource in scope, named as an offence over one would name it.
     *
     * <p>A rule that resolves a name against the documents a module ships needs the documents that
     * declare nothing as much as the ones that declare something, since a template holding no fragment
     * at all is still a template a view name reaches. Gathering alone would leave those out, because a
     * document contributes to it only by declaring something.
     *
     * @return the files, relative to the repository root, in the order git reports them
     */
    List<Path> files() {
        return this.files.stream().map(this.root::relativize).toList();
    }

    /**
     * Every offence the given reading finds, across every file in scope.
     *
     * @param reading what the calling check makes of one document
     * @return the offences, in the order the files were read
     */
    List<String> offences(BiFunction<Path, Collection<String>, MarkupElement> reading) {
        return this.gathered(reading);
    }

    /**
     * Everything the given reading keeps, across every file in scope.
     *
     * @param reading what the calling check makes of one document
     * @param <T>     what the calling check keeps about a document
     * @return what was kept, in the order the files were read
     */
    <T> List<T> gathered(BiFunction<Path, Collection<T>, MarkupElement> reading) {
        List<T> kept = new ArrayList<>();
        this.files.forEach(file -> this.read(file, reading, kept));
        return List.copyOf(kept);
    }

    private <T> void read(
        Path file, BiFunction<Path, Collection<T>, MarkupElement> reading, Collection<T> kept
    ) {
        Repository.readText(file)
            .ifPresent(text -> kept.addAll(elementsIn(this.root.relativize(file), text, reading)));
    }

    private static <T> List<T> elementsIn(
        Path named, String text, BiFunction<Path, Collection<T>, MarkupElement> reading
    ) {
        List<T> found = new ArrayList<>();
        try {
            new SimpleMarkupParser(ParseConfiguration.htmlConfiguration())
                .parse(text, new Elements(reading.apply(named, found)));
            return List.copyOf(found);
        } catch (ParseException _) {
            // Markup no engine could read is nobody's finding to make here. template-parse reports it
            // from the same files, with the line and column of what it could not read, and a build fails
            // on that before anybody asks what its elements carry. Whatever the parser reached before it
            // stopped is half a document, so this contributes nothing rather than a verdict taken from a
            // file that has no settled contents.
            return List.of();
        }
    }

    /**
     * Hands the parser's elements to one reader, however a document happens to close them.
     */
    private static final class Elements extends AbstractSimpleMarkupHandler {

        private final MarkupElement reader;

        private Elements(MarkupElement reader) {
            this.reader = reader;
        }

        @Override
        public void handleOpenElement(
            String element, @Nullable Map<String, String> attributes, int line, int column
        ) {
            this.hand(attributes, line, column);
        }

        // The list is attoparser's rather than this code's, which is why the parameter cap passes over
        // an override. Declining it would leave the reader uncalled for an element that closes itself,
        // and whatever was written on one unread.
        @Override
        public void handleStandaloneElement(
            String element, @Nullable Map<String, String> attributes, boolean minimized, int line, int column
        ) {
            this.hand(attributes, line, column);
        }

        // The list is attoparser's here too, and for the same reason the cap passes over it. The text
        // arrives as a window on the parser's own buffer rather than as a string, so it is read out
        // here and handed on as one, which keeps every reader from repeating that.
        @Override
        public void handleText(char[] buffer, int offset, int length, int line, int column) {
            this.reader.text(new String(buffer, offset, length), line, column);
        }

        // An element carrying no attribute at all arrives with nothing rather than with an empty map,
        // which is the parser saying the same thing in a way its caller has to answer for. It is
        // answered once, here, so that no reader of an element has to.
        private void hand(@Nullable Map<String, String> attributes, int line, int column) {
            this.reader.read(Optional.ofNullable(attributes).orElseGet(Map::of), line, column);
        }
    }
}
