package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Holds a project file to one layout, so every pom reads the same way and a diff shows only what changed.
 *
 * <p>A block is an element written directly inside {@code <project>} that holds elements, such as
 * {@code <parent>}, {@code <licenses>} or {@code <build>}, and exactly one blank line sets it apart from each
 * neighbour. Comments written directly above a block, with no blank line between them, belong to it, so the
 * blank line stands above them. The rule stops at the top level: inside a block, related elements stay together.
 * Two consecutive blank lines are refused anywhere, and so is a blank line directly after an opening tag or
 * directly before a closing one. Every tag starts its own line, apart from the closing tag of an element that
 * holds only text, so {@code <name>MIT</name>} stays on one line and {@code <license><name>} does not.
 */
public final class PomLayoutCheck {

    private static final int ONE_BLANK_LINE = 2;

    private final Path pom;

    /**
     * Records the project file the check reads.
     *
     * @param pom the project file
     */
    public PomLayoutCheck(Path pom) {
        this.pom = pom;
    }

    /**
     * Reports every place the project file departs from the layout.
     *
     * @return one group of findings per layout rule
     */
    public List<Findings> findings() {
        String content = read(this.pom);
        List<PomTag> tags = PomMarkup.read(content);
        return List.of(
            new Findings(
                "Set every element directly inside <project> that holds elements apart from its neighbours by "
                    + "exactly one blank line, above the comments written directly over it",
                this.render(separations(siblings(tags)))
            ),
            new Findings("Write no two consecutive blank lines in a pom", this.render(blankRuns(content))),
            new Findings(
                "Start every pom tag on its own line, apart from the closing tag of an element that holds only text",
                this.render(crowded(tags))
            ),
            new Findings(
                "Write no blank line directly after an opening pom tag or directly before a closing one",
                this.render(edges(tags))
            )
        );
    }

    private static List<PomSibling> siblings(List<PomTag> tags) {
        return IntStream.range(0, tags.size())
            .filter(index -> tags.get(index).depth() == 1 && tags.get(index).kind() != PomTag.Kind.CLOSE)
            .mapToObj(index -> sibling(tags, index))
            .toList();
    }

    private static PomSibling sibling(List<PomTag> tags, int index) {
        PomTag tag = tags.get(index);
        boolean comment = tag.kind() == PomTag.Kind.COMMENT;
        boolean block = !comment && holdsElements(tags, index, close(tags, index));
        return new PomSibling(tag.label(), comment, block, tag.line(), tag.newlinesBefore());
    }

    private static List<Located<String>> crowded(List<PomTag> tags) {
        return IntStream.range(0, tags.size())
            .filter(index -> !tags.get(index).breaksLine() && startsItsLine(tags, index))
            .mapToObj(tags::get)
            .map(tag -> new Located<>(tag.line(), tag.label() + " follows other markup on its line"))
            .toList();
    }

    private static boolean startsItsLine(List<PomTag> tags, int index) {
        PomTag tag = tags.get(index);
        return switch (tag.kind()) {
            case OPEN -> tag.depth() > 0;
            case CLOSE -> holdsElements(tags, open(tags, index), index);
            case COMMENT -> false;
        };
    }

    private static List<Located<String>> edges(List<PomTag> tags) {
        return IntStream.range(1, tags.size())
            .filter(index -> tags.get(index).blankBefore())
            .mapToObj(index -> edge(tags.get(index - 1), tags.get(index)))
            .flatMap(Optional::stream)
            .toList();
    }

    private static Optional<Located<String>> edge(PomTag before, PomTag tag) {
        return edgePlace(before, tag).map(place -> new Located<>(tag.firstBlankLine(), "blank line " + place));
    }

    private static Optional<String> edgePlace(PomTag before, PomTag tag) {
        if (before.kind() == PomTag.Kind.OPEN) {
            return Optional.of(
                tag.kind() == PomTag.Kind.CLOSE
                    ? "inside " + before.label() + ", which holds nothing else"
                    : "directly after " + before.label()
            );
        }
        return Optional.of("directly before " + tag.label()).filter(_ -> tag.kind() == PomTag.Kind.CLOSE);
    }

    private static int close(List<PomTag> tags, int open) {
        int depth = tags.get(open).depth();
        return IntStream.range(open + 1, tags.size())
            .filter(index -> tags.get(index).is(PomTag.Kind.CLOSE, depth))
            .findFirst()
            .orElseThrow();
    }

    private static int open(List<PomTag> tags, int close) {
        int depth = tags.get(close).depth();
        return IntStream.iterate(close - 1, index -> index >= 0, index -> index - 1)
            .filter(index -> tags.get(index).is(PomTag.Kind.OPEN, depth))
            .findFirst()
            .orElseThrow();
    }

    private static boolean holdsElements(List<PomTag> tags, int open, int close) {
        return IntStream.range(open + 1, close).anyMatch(index -> tags.get(index).kind() == PomTag.Kind.OPEN);
    }

    private static List<Located<String>> separations(List<PomSibling> siblings) {
        return IntStream.range(0, siblings.size())
            .filter(index -> siblings.get(index).block())
            .flatMap(index -> IntStream.of(unitStart(siblings, index), index + 1))
            .filter(gap -> gap > 0 && gap < siblings.size())
            .distinct()
            .sorted()
            .filter(gap -> siblings.get(gap).newlinesBefore() != ONE_BLANK_LINE)
            .mapToObj(gap -> separation(siblings.get(gap - 1), siblings.get(gap)))
            .toList();
    }

    private static int unitStart(List<PomSibling> siblings, int block) {
        int start = block;
        while (start > 0 && siblings.get(start - 1).comment() && siblings.get(start).newlinesBefore() == 1) {
            start--;
        }
        return start;
    }

    private static Located<String> separation(PomSibling before, PomSibling after) {
        int blankLines = Math.max(0, after.newlinesBefore() - 1);
        String written = blankLines == 0 ? "no blank line" : blankLines + " blank lines";
        return new Located<>(
            after.line(), "%s between %s and %s".formatted(written, before.label(), after.label())
        );
    }

    private static List<Located<String>> blankRuns(String content) {
        List<Boolean> blank = content.lines().map(String::isBlank).toList();
        return IntStream.range(1, blank.size())
            .filter(index -> blank.get(index) && blank.get(index - 1))
            .filter(index -> index == 1 || !blank.get(index - 2))
            .mapToObj(index -> new Located<>(index + 1, "a second consecutive blank line"))
            .toList();
    }

    private List<String> render(List<Located<String>> offences) {
        Path name = this.pom.getFileName();
        return offences.stream()
            .map(offence -> "%s:%d: %s".formatted(name, offence.line(), offence.value()))
            .toList();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }
}
