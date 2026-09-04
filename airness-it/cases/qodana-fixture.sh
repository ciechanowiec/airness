#!/usr/bin/env sh

create_qodana_fixture() {
    new_consumer qodana-profile
    qodana_consumer="$consumer_directory"
    cat > "$qodana_consumer/src/main/java/com/example/CommandRequest.java" <<'JAVA'
package com.example;

/**
 * A closed set of requests, laid out with its permitted records inside the sealed parent.
 */
public sealed interface CommandRequest {

    /**
     * Describes the request.
     *
     * @return the description
     */
    String description();

    /**
     * Starts a project.
     *
     * @param root what the request names
     */
    record Init(String root) implements CommandRequest {

        @Override
        public String description() {
            return "init";
        }
    }

    /**
     * Reads a source without changing it.
     *
     * @param source what the request names
     */
    record Lint(String source) implements CommandRequest {

        @Override
        public String description() {
            return "lint";
        }
    }

    /**
     * Produces the artifact.
     *
     * @param target what the request names
     */
    record Build(String target) implements CommandRequest {

        @Override
        public String description() {
            return "build";
        }
    }

    /**
     * Discards produced output.
     *
     * @param scope what the request names
     */
    record Clean(String scope) implements CommandRequest {

        @Override
        public String description() {
            return "clean";
        }
    }

    /**
     * Rewrites a source into its canonical shape.
     *
     * @param style what the request names
     */
    record Format(String style) implements CommandRequest {

        @Override
        public String description() {
            return "format";
        }
    }

    /**
     * Sends the artifact to a registry.
     *
     * @param registry what the request names
     */
    record Publish(String registry) implements CommandRequest {

        @Override
        public String description() {
            return "publish";
        }
    }

    /**
     * Writes the findings of a run.
     *
     * @param findings what the request names
     */
    record Report(String findings) implements CommandRequest {

        @Override
        public String description() {
            return "report";
        }
    }

    /**
     * Decides whether the project holds.
     *
     * @param rules what the request names
     */
    record Verify(String rules) implements CommandRequest {

        @Override
        public String description() {
            return "verify";
        }
    }

    /**
     * Repeats a run whenever a source changes.
     *
     * @param trigger what the request names
     */
    record Watch(String trigger) implements CommandRequest {

        @Override
        public String description() {
            return "watch";
        }
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/Channel.java" <<'JAVA'
package com.example;

/**
 * Where a run writes, which is one place and therefore one word.
 */
public enum Channel {

    /**
     * The standard output of the process.
     */
    STANDARD_OUTPUT
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/OutputFormat.java" <<'JAVA'
package com.example;

/**
 * The shapes a run can report in.
 */
public enum OutputFormat {

    /**
     * Plain text.
     */
    TEXT,

    /**
     * Structured JSON.
     */
    JSON;

    /**
     * Reads a format from its written name.
     *
     * @param name the written name
     * @return the format the name stands for
     */
    public static OutputFormat of(String name) {
        return "json".equals(name) ? JSON : TEXT;
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/ParseResult.java" <<'JAVA'
package com.example;

/**
 * The outcome of reading one request.
 *
 * @param name the name the request parsed to
 */
public record ParseResult(String name) {
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/SafePath.java" <<'JAVA'
package com.example;

import java.nio.file.Path;

/**
 * A path the run is allowed to reach.
 *
 * @param value the permitted path
 */
public record SafePath(Path value) {
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/BrokenRunException.java" <<'JAVA'
package com.example;

/**
 * Reports a run that could not finish.
 */
public final class BrokenRunException extends RuntimeException {

    /**
     * Reports the failure on its own.
     *
     * @param message what went wrong
     */
    public BrokenRunException(String message) {
        super(message);
    }

    /**
     * Reports the failure together with its cause.
     *
     * @param message what went wrong
     * @param cause   the failure underneath
     */
    public BrokenRunException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Sums the failure up in one line.
     *
     * @return the summary of the failure
     */
    public String summary() {
        return "broken run: " + this.getMessage();
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/CommandGrammar.java" <<'JAVA'
package com.example;

/**
 * Reads a request into the name the run works with.
 */
public final class CommandGrammar {

    /**
     * Reads one request.
     *
     * @param request the request to read
     * @return what the request parsed to
     */
    public ParseResult parse(CommandRequest request) {
        return new ParseResult(request.description());
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/ProjectRepository.java" <<'JAVA'
package com.example;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The project a run reads.
 */
public final class ProjectRepository {

    private final Path root;

    /**
     * Creates the repository.
     *
     * @param root where the project sits
     */
    public ProjectRepository(Path root) {
        this.root = root;
    }

    /**
     * Locates what a parsed request names.
     *
     * @param result what the request parsed to
     * @return the located path, empty when the name reaches nothing
     */
    public Optional<SafePath> locate(ParseResult result) {
        return result.name().isEmpty()
            ? Optional.empty()
            : Optional.of(new SafePath(this.root.resolve(result.name())));
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/CommandRouter.java" <<'JAVA'
package com.example;

/**
 * Reaches every request shape a project offers.
 *
 * <p>The class names sixteen collaborators of its own project and none of the JDK, so it is what a
 * genuinely entangled class looks like once the JDK stops counting.
 */
public final class CommandRouter {

    /**
     * Routes the request shapes a project starts with.
     *
     * @param init  the start request
     * @param lint  the read-only request
     * @param build the producing request
     * @param clean the discarding request
     * @return what the four requests name
     */
    public String opening(
        CommandRequest.Init init, CommandRequest.Lint lint,
        CommandRequest.Build build, CommandRequest.Clean clean
    ) {
        return init.root() + lint.source() + build.target() + clean.scope();
    }

    /**
     * Routes the request shapes a project finishes with.
     *
     * @param format  the rewriting request
     * @param publish the sending request
     * @param report  the writing request
     * @param verify  the deciding request
     * @return what the four requests name
     */
    public String closing(
        CommandRequest.Format format, CommandRequest.Publish publish,
        CommandRequest.Report report, CommandRequest.Verify verify
    ) {
        return format.style() + publish.registry() + report.findings() + verify.rules();
    }

    /**
     * Routes a repeated run against what it locates.
     *
     * @param watch      the repeating request
     * @param repository the project the run reads
     * @param fallback   where the run reads when the name reaches nothing
     * @param result     what the request parsed to
     * @return what the repeated run reaches
     */
    public String repeated(
        CommandRequest.Watch watch, ProjectRepository repository,
        SafePath fallback, ParseResult result
    ) {
        return watch.trigger()
            + repository.locate(result).orElse(fallback).value()
            + result.name();
    }

    /**
     * Routes a request that has not been read yet.
     *
     * @param grammar the grammar the request is read with
     * @param output  the shape the run reports in
     * @param failure the failure a broken run carries
     * @param request the request to read
     * @return what the unread request reaches
     */
    public String unread(
        CommandGrammar grammar, OutputFormat output,
        BrokenRunException failure, CommandRequest request
    ) {
        return grammar.parse(request).name() + output.name() + failure.summary();
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/CommandRunner.java" <<'JAVA'
package com.example;

/**
 * Reads twelve request shapes of its own project.
 *
 * <p>Twelve is over the cap the dropped inspection held a class to and under the cap ClassCoupling
 * declares, so the class is exactly what the change let a consumer write.
 */
public final class CommandRunner {

    /**
     * Reads the request shapes a project starts with.
     *
     * @return what those requests describe
     */
    public String opening() {
        return new CommandRequest.Init("root").description()
            + new CommandRequest.Lint("source").description()
            + new CommandRequest.Build("target").description()
            + new CommandRequest.Clean("scope").description();
    }

    /**
     * Reads the request shapes a project finishes with.
     *
     * @return what those requests describe
     */
    public String closing() {
        return new CommandRequest.Format("style").description()
            + new CommandRequest.Publish("registry").description()
            + new CommandRequest.Report("findings").description()
            + new CommandRequest.Verify("rules").description();
    }

    /**
     * Reads a repeated run.
     *
     * @return what the repeated run reports in
     */
    public String repeated() {
        CommandRequest request = new CommandRequest.Watch("changes");
        ParseResult result = new ParseResult(request.description());
        OutputFormat output = OutputFormat.of(result.name());
        return output.name();
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/JdkHeavy.java" <<'JAVA'
package com.example;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Names fourteen JDK types beside a single collaborator of its own project.
 *
 * <p>No coupling rule counts what this class reaches into java.util and java.time, so the class is
 * held to a budget of one.
 */
public final class JdkHeavy {

    /**
     * Reads every JDK type this fixture is built from.
     *
     * @param format the one collaborator the class names
     * @return a reading of the types above
     */
    public String reading(OutputFormat format) {
        Deque<String> queue = new ArrayDeque<>(List.of(format.name()));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(queue.peek(), 1);
        NavigableSet<String> sorted = new TreeSet<>(counts.keySet());
        Set<OutputFormat> formats = EnumSet.of(format);
        Optional<String> first = Optional.of(sorted.getFirst());
        Duration span = Duration.between(Instant.EPOCH, Instant.EPOCH);
        LocalDate day = LocalDate.EPOCH;
        String read = sorted.stream().collect(Collectors.joining(", ", "[", "]"));
        return first.orElse("none")
            + " / " + formats.size()
            + " / " + span.toMillis()
            + " / " + day.getDayOfYear()
            + " / " + read;
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/Tool.java" <<'JAVA'
package com.example;

/**
 * A static-only class that declares a main and carries no annotation.
 */
public final class Tool {

    /**
     * Runs the tool.
     *
     * @param args the command line arguments
     */
    static void main(String[] args) {
        IO.println(args.length);
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/Chart.java" <<'JAVA'
package com.example;

/**
 * A chart whose parts are named for what they are, in words the questionable-name list once held.
 */
public final class Chart {

    private final int longest;

    /**
     * Draws every bar against the given longest one.
     *
     * @param longest the longest bar of the chart
     */
    public Chart(int longest) {
        this.longest = longest;
    }

    /**
     * Answers the given bar cut to the longest, which is how far a bar may run.
     *
     * @param bar the bar to draw
     * @return the bar, or the longest where the bar is longer
     */
    public int drawn(int bar) {
        int then = this.longest;
        return bar > then ? then : bar;
    }

    /**
     * Answers the given placeholder unchanged, which is the control the profile still refuses.
     *
     * @param foo a name that says nothing
     * @return the same value
     */
    public int control(int foo) {
        return foo;
    }
}
JAVA
    cat > "$qodana_consumer/src/main/java/com/example/Budget.java" <<'JAVA'
package com.example;

/**
 * A budget counted in whole units, whose figures sit inside the allowlist the harness wrote down.
 */
public final class Budget {

    private final long allowance;

    /**
     * Sets the budget.
     *
     * @param allowance how much the budget allows
     */
    public Budget(long allowance) {
        this.allowance = allowance;
    }

    /**
     * Answers the allowance cut to the three units a run may spend at once.
     *
     * @return the allowance, or three where the allowance is larger
     */
    public long spendable() {
        return this.allowance > 3L ? 3L : this.allowance;
    }
}
JAVA
    git -C "$qodana_consumer" add --all
    git -C "$qodana_consumer" commit --quiet \
        --message 'test(it): carry the shapes the dropped inspections reported' \
        --message 'The fixture holds a sealed hierarchy, an over-coupled class, a class inside the band that moved, a JDK-heavy class, a chart named in domain words, an enum of one constant and a budget counted in long units, so the profile has something to be read against.'
}
