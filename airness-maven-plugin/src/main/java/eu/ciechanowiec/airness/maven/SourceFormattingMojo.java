package eu.ciechanowiec.airness.maven;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.revelc.code.formatter.ConfigurationSource;
import net.revelc.code.formatter.java.JavaFormatter;
import net.revelc.code.formatter.model.ConfigReader;
import net.revelc.code.impsort.Grouper;
import net.revelc.code.impsort.ImpSort;
import net.revelc.code.impsort.Result;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Checks Java formatting and import order without editing source files.
 *
 * <p>The third-party validation goals throw as soon as they find an offence and offer no report-only
 * mode. Calling their formatting engines directly preserves the exact formatting behaviour while
 * letting {@link GovernanceMojo} report every file and apply the single Airness enforcement switch.
 */
@Mojo(name = "source-formatting", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, threadSafe = true)
public class SourceFormattingMojo extends GovernanceMojo {

    private static final String PROFILE = "eu/ciechanowiec/airness/formatting/EclipseCodeStyle.xml";

    @Override
    protected boolean applies() {
        return !this.moduleSourceRoots().isEmpty();
    }

    @Override
    protected List<Findings> findings() {
        List<Path> sources = javaSources(this.moduleSourceRoots());
        JavaFormatter formatter = formatter(this.getLog(), this.project().getBuild().getDirectory());
        ImpSort sorter = sorter();
        if (this.formatProfile()) {
            sources.forEach(source -> apply(formatter, sorter, source));
        }
        return List.of(
            new Findings("Java sources that do not match the Airness formatter", sources.stream()
                .filter(source -> !formatted(formatter, source)).map(this::relative).toList()),
            new Findings("Java sources whose imports are not normalized", sources.stream()
                .filter(source -> !sorted(sorter, source)).map(this::relative).toList())
        );
    }

    static List<Path> javaSources(List<Path> roots) {
        List<Path> sources = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(sources::add);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not enumerate Java sources under " + root, exception);
            }
        }
        return List.copyOf(sources);
    }

    static JavaFormatter formatter(Log log, String target) {
        try (InputStream stream = SourceFormattingMojo.class.getClassLoader().getResourceAsStream(PROFILE)) {
            if (stream == null) {
                throw new IllegalStateException("Airness formatter profile is missing: " + PROFILE);
            }
            Map<String, String> options = new ConfigReader().read(stream);
            JavaFormatter formatter = new JavaFormatter();
            formatter.init(options, new SourceConfiguration(log, target));
            return formatter;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not initialize the Airness formatter", exception);
        }
    }

    private String relative(Path source) {
        return this.project().getBasedir().toPath().relativize(source).toString();
    }

    static boolean formatted(JavaFormatter formatter, Path source) {
        try {
            String held = Files.readString(source);
            return held.equals(formattedSource(formatter, source));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not format " + source, exception);
        }
    }

    static String formattedSource(JavaFormatter formatter, Path source) {
        try {
            return formatter.doFormat(
                Files.readString(source), net.revelc.code.formatter.LineEnding.LF
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not format " + source, exception);
        }
    }

    static ImpSort sorter() {
        return new ImpSort(
            StandardCharsets.UTF_8, new Grouper("*", "*", false, false, false), true, false,
            net.revelc.code.impsort.LineEnding.LF, LanguageLevel.JAVA_25, false
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

    private boolean formatProfile() {
        return this.project().getActiveProfiles().stream().anyMatch(profile -> "format".equals(profile.getId()));
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

    /** Supplies the compiler and filesystem settings required by the formatter engine. */
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
