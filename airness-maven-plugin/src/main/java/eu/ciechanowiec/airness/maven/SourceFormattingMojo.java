package eu.ciechanowiec.airness.maven;

import static net.revelc.code.formatter.LineEnding.LF;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.Problem;
import eu.ciechanowiec.airness.governance.Findings;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.revelc.code.formatter.ConfigurationSource;
import net.revelc.code.formatter.java.JavaFormatter;
import net.revelc.code.formatter.model.ConfigReadException;
import net.revelc.code.formatter.model.ConfigReader;
import net.revelc.code.impsort.Grouper;
import net.revelc.code.impsort.ImpSort;
import net.revelc.code.impsort.LineEnding;
import net.revelc.code.impsort.Result;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.jface.text.BadLocationException;
import org.xml.sax.SAXException;

/**
 * Checks Java formatting and import order, and rewrites the sources when asked to.
 *
 * <p>The third-party validation goals throw as soon as they find an offence and offer no report-only
 * mode. Calling their formatting engines directly preserves the exact formatting behaviour while
 * letting {@link AbstractGovernanceMojo} report every file and apply the single Airness enforcement switch.
 *
 * <p>Reporting is what an ordinary build gets. Writing happens only when
 * {@code airness.source.formatting.apply} is set, which the parent does under its {@code format}
 * profile and nowhere else, so the repair stays a command somebody ran on purpose.
 */
@Mojo(name = "source-formatting", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, threadSafe = true)
public final class SourceFormattingMojo extends AbstractGovernanceMojo {

    private static final String PROFILE = "eu/ciechanowiec/airness/formatting/EclipseCodeStyle.xml";

    @Parameter(property = "airness.source.formatting.apply", defaultValue = "false")
    private boolean apply;

    @Override
    boolean applies() {
        return this.hasModuleJava();
    }

    @Override
    List<Findings> findings() {
        List<Path> sources = javaSources(this.moduleSourceRoots());
        JavaFormatter formatter = formatter(this.getLog(), this.project().getBuild().getDirectory());
        ImpSort sorter = sorter();
        if (this.apply) {
            sources.forEach(source -> apply(formatter, sorter, source));
        }
        return List.of(
            new Findings(
                "Java sources that do not match the Airness formatter", sources.stream()
                    .filter(source -> !formatted(formatter, source)).map(this::relative).toList()
            ),
            new Findings(
                "Java sources whose imports are not normalized", sources.stream()
                    .filter(source -> !sorted(sorter, source)).map(this::relative).toList()
            )
        );
    }

    static List<Path> javaSources(Iterable<Path> roots) {
        return StreamSupport.stream(roots.spliterator(), false)
            .flatMap(root -> javaSources(root).stream())
            .toList();
    }

    private static List<Path> javaSources(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not enumerate Java sources under " + root, exception);
        }
    }

    static JavaFormatter formatter(Log log, String target) {
        try (InputStream stream = profile()) {
            Map<String, String> options = new ConfigReader().read(stream);
            JavaFormatter formatter = new JavaFormatter();
            formatter.init(options, new SourceConfiguration(log, target));
            return formatter;
        } catch (IOException | SAXException | ConfigReadException exception) {
            throw new IllegalStateException("Could not initialize the Airness formatter", exception);
        }
    }

    private static InputStream profile() {
        return Optional.ofNullable(SourceFormattingMojo.class.getClassLoader().getResourceAsStream(PROFILE))
            .orElseThrow(() -> new IllegalStateException("Airness formatter profile is missing: " + PROFILE));
    }

    private String relative(Path source) {
        return this.project().getBasedir().toPath().relativize(source).toString();
    }

    static boolean formatted(JavaFormatter formatter, Path source) {
        try {
            String held = Files.readString(source);
            return held.equals(formattedSource(formatter, source));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not format " + source, exception);
        }
    }

    static String formattedSource(JavaFormatter formatter, Path source) {
        try {
            String held = Files.readString(source);
            String eclipseFormatted = Optional.ofNullable(formatter.doFormat(held, LF)).orElse(held);
            return ParameterAnnotationFormatting.normalized(eclipseFormatted);
        } catch (IOException | BadLocationException exception) {
            throw new IllegalStateException("Could not format " + source, exception);
        }
    }

    static ImpSort sorter() {
        return new ImpSort(
            StandardCharsets.UTF_8, new Grouper("*", "*", false, false, false), true, false,
            LineEnding.LF, LanguageLevel.JAVA_25, false
        );
    }

    static boolean sorted(ImpSort sorter, Path source) {
        try {
            Result result = sorter.parseFile(source);
            List<Problem> problems = result.getReportableProblems();
            if (!problems.isEmpty()) {
                throw new IllegalStateException("Could not parse " + source + ": " + problems);
            }
            return result.isSorted();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect imports in " + source, exception);
        }
    }

    private static void apply(JavaFormatter formatter, ImpSort sorter, Path source) {
        try {
            Files.writeString(source, formattedSource(formatter, source));
            Result imports = sorter.parseFile(source);
            if (!imports.getReportableProblems().isEmpty()) {
                throw new IllegalStateException("Could not parse " + source + ": " + imports.getReportableProblems());
            }
            imports.saveSorted(source);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not normalize " + source, exception);
        }
    }

    /**
     * Supplies the compiler and filesystem settings required by the formatter engine.
     */
    private record SourceConfiguration(Log log, String target) implements ConfigurationSource {

        @Override
        public Log getLog() {
            return this.log;
        }

        @Override
        public String getCompilerSources() {
            return "25";
        }

        @Override
        public String getCompilerCompliance() {
            return "25";
        }

        @Override
        public String getCompilerCodegenTargetPlatform() {
            return "25";
        }

        @Override
        public Path getTargetDirectory() {
            return Path.of(this.target);
        }

        @Override
        public Charset getEncoding() {
            return StandardCharsets.UTF_8;
        }
    }
}
