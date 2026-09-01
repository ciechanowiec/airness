package eu.ciechanowiec.airness.maven;

import static com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_25;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;

/**
 * Finishes the parameter-annotation layout that Eclipse JDT cannot express conditionally.
 *
 * <p>JDT can either join every parameter annotation to the declaration or split every one from it. The
 * Airness form is conditional: a compact group stays compact, while the declaration after a group that
 * already spans lines starts on a new line. This pass runs after JDT and makes that form idempotent.
 */
@UtilityClass
final class ParameterAnnotationFormatting {

    private static final int MULTIPLE_ANNOTATIONS = 2;
    private static final String NEWLINE = "\n";
    private static final char NEWLINE_CHARACTER = '\n';
    private static final Pattern HORIZONTAL_WHITESPACE = Pattern.compile("[ \\t]+");

    static String normalized(String source) {
        SourceText text = SourceText.of(source);
        List<Replacement> replacements = compilationUnit(source).findAll(Parameter.class).stream()
            .map(parameter -> replacement(text, parameter))
            .flatMap(Optional::stream)
            .sorted(Comparator.comparingInt(Replacement::start).reversed())
            .toList();
        StringBuilder normalized = new StringBuilder(source);
        replacements.forEach(
            replacement -> normalized.replace(
                replacement.start(), replacement.end(), replacement.text()
            )
        );
        return normalized.toString();
    }

    private static Optional<Replacement> replacement(SourceText text, Parameter parameter) {
        List<AnnotationExpr> annotations = parameter.getAnnotations();
        if (annotations.size() < MULTIPLE_ANNOTATIONS) {
            return Optional.empty();
        }
        Range first = range(annotations.getFirst());
        Range last = range(annotations.getLast());
        return replacement(text, first, last);
    }

    private static Optional<Replacement> replacement(SourceText text, Range first, Range last) {
        if (first.begin.line == last.end.line) {
            return Optional.empty();
        }
        return replacement(text, last);
    }

    private static Optional<Replacement> replacement(SourceText text, Range annotation) {
        int afterAnnotation = text.offset(annotation.end) + 1;
        Matcher spacing = HORIZONTAL_WHITESPACE.matcher(text.source());
        spacing.region(afterAnnotation, text.source().length());
        if (!spacing.lookingAt() || text.source().charAt(spacing.end()) == NEWLINE_CHARACTER) {
            return Optional.empty();
        }
        String indentation = text.line(annotation.begin).substring(0, annotation.begin.column - 1);
        return Optional.of(new Replacement(afterAnnotation, spacing.end(), NEWLINE + indentation));
    }

    private static CompilationUnit compilationUnit(String source) {
        JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(JAVA_25));
        ParseResult<CompilationUnit> result = parser.parse(source);
        return result.getResult().filter(_ -> result.isSuccessful()).orElseThrow(
            () -> new IllegalStateException("Could not parse formatted Java source: " + result.getProblems())
        );
    }

    private static Range range(AnnotationExpr annotation) {
        return annotation.getRange().orElseThrow(
            () -> new IllegalStateException("A parsed parameter annotation has no source range")
        );
    }

    private record Replacement(int start, int end, String text) {
    }

    private record SourceText(String source, List<String> lines) {

        private static SourceText of(String source) {
            return new SourceText(source, Arrays.asList(source.split(NEWLINE, -1)));
        }

        private int offset(Position position) {
            return IntStream.range(0, position.line - 1)
                .map(index -> this.lines.get(index).length() + 1)
                .sum() + position.column - 1;
        }

        private String line(Position position) {
            return this.lines.get(position.line - 1);
        }
    }
}
