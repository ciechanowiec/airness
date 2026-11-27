package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Every action the repository's CI uses declares a Node runtime GitHub still supports, or runs no Node at all.
 *
 * <p>An action states the runtime it runs on in the {@code runs.using} key of its own metadata, its
 * {@code action.yml} or {@code action.yaml}. When GitHub deprecates a runtime, a runner keeps running such an
 * action on a later Node and only annotates the run, so a workflow pinned to an old release passes until the day
 * the runtime is removed and then fails with no earlier warning. The only repair is a release of the action whose
 * metadata declares a supported runtime, which is the only thing a finding asks for.
 *
 * <p>The files are the GitHub Actions workflows and composite actions {@link BlocklistFiles} finds. Each
 * {@code uses} value names an action in another repository at a ref, and the runtime is read from the metadata
 * that ref holds, never from the comment a workflow writes beside a pinned commit, which nothing keeps true. A
 * {@code docker} action runs no Node and passes. A {@code composite} action runs its own steps, so each action
 * those steps use is judged the same way, as deep as GitHub nests composite actions, and a refusal found there is
 * reported at the line of the workflow that reached it, naming the action that declares the runtime. A local
 * action ({@code ./path}) is judged in its own metadata, where it lives, when that file is one of the composite
 * actions {@link BlocklistFiles} finds. A Docker action ({@code docker://image}) is judged by the blocklist, and
 * a reusable workflow is not an action. A {@code uses} value holding an expression, outside the form GitHub reads
 * an action in, or naming a ref that holds no metadata, and metadata that declares no runtime GitHub runs actions
 * on, cannot be judged, and the rule fails on them rather than passing them, as {@link ActionUses} explains.
 *
 * <p>actionlint, which Extended verification runs over every workflow, warns about an action whose runner is too
 * old only by looking the whole {@code uses} value up, as written, in a table of popular actions by version tag.
 * Its release 1.7.12 lists only the tags of the {@code node12} and {@code node16} releases, such as
 * {@code actions/checkout@v3}, and no commit at all, so an action pinned by commit, which is how a supply chain is
 * pinned, and every {@code node20} release are never judged there. Reading the metadata at the pinned ref is what
 * judges both.
 *
 * <p>A Node runtime later than {@value #REQUIRED_RUNTIME} passes, so a project can move as soon as its actions
 * publish one and is not stopped until the harness has moved too.
 */
public final class ActionRuntimeCheck {

    /**
     * The earliest Node runtime an action is allowed to declare, as the number in {@code nodeNN}.
     *
     * <p>Moved to the next runtime only once GitHub has deprecated this one for actions, as it deprecated
     * {@code node20} in September 2025, and never merely because a later runtime exists. Moved earlier, it fails
     * every governed build whose actions have published no release on the later runtime yet, with no release to
     * move to.
     */
    static final int REQUIRED_RUNTIME = 24;

    /**
     * How deep composite actions are followed: the depth to which GitHub nests composite actions.
     */
    static final int NESTING_LIMIT = 10;

    private static final String REQUIRED = "node%d".formatted(REQUIRED_RUNTIME);
    private static final String OUTDATED_HEADLINE = "Actions a CI file uses that declare a Node runtime below "
        + REQUIRED + " (pin a release whose metadata declares " + REQUIRED + " or later)";
    private static final String UNREAD_HEADLINE = "Actions a CI file uses whose runtime cannot be read, so the Node"
        + " runtime rule fails rather than passing without them";
    private static final String DEPRECATED = ", a Node runtime GitHub has deprecated";
    private static final String COMPOSITE = "composite";
    private static final String DOCKER = "docker";
    private static final String RUNTIMES = REQUIRED + " or later, docker, or composite";
    private static final Path ACTIONS = Path.of(".github", "actions");
    private static final char LINE_SEPARATOR = ':';

    private final ActionMetadataSource source;
    private final Map<ActionReference, Optional<String>> fetched;
    private final int scanned;
    private final List<String> outdated;
    private final List<String> unread;

    /**
     * Reads every workflow and composite action of the working tree once, and the metadata of every action they
     * use once.
     *
     * @param root   the working tree root
     * @param source where the metadata of an action in another repository is read from
     * @throws IllegalStateException when the source could not answer
     */
    public ActionRuntimeCheck(Path root, ActionMetadataSource source) {
        this.source = source;
        this.fetched = new HashMap<>();
        List<Path> files = BlocklistFiles.workflows(root, Repository.trackedFiles(root));
        this.scanned = files.size();
        List<Offence> offences = files.stream().flatMap(file -> this.offences(root, file)).toList();
        this.outdated = offences.stream().filter(Offence::outdated).map(Offence::text).toList();
        this.unread = offences.stream().filter(offence -> !offence.outdated()).map(Offence::text).toList();
    }

    /**
     * How many CI files the check read, which a caller logs so the reach of a clean verdict is on the record.
     *
     * @return the number of files read
     */
    public int scanned() {
        return this.scanned;
    }

    /**
     * How many distinct actions in other repositories the check read the metadata of.
     *
     * @return the number of actions read
     */
    public int actions() {
        return this.fetched.size();
    }

    /**
     * Every action that declares a deprecated Node runtime, then every action whose runtime cannot be read.
     *
     * @return the two verdicts this check produces
     */
    public List<Findings> findings() {
        return List.of(new Findings(OUTDATED_HEADLINE, this.outdated), new Findings(UNREAD_HEADLINE, this.unread));
    }

    private Stream<Offence> offences(Path root, Path file) {
        Path relative = root.relativize(file);
        String name = relative.toString();
        String text = Repository.readText(file).orElse("");
        Stream<Offence> own = relative.startsWith(ACTIONS) ? ownRuntime(name, ActionMetadata.read(text))
            : Stream.empty();
        Stream<Offence> used = WorkflowFile.actions(text).stream()
            .flatMap(use -> this.judged(name + LINE_SEPARATOR + use.line(), use.value()));
        return Stream.concat(own, used);
    }

    // A local action is judged in its own file, at the line that declares its runtime.
    private static Stream<Offence> ownRuntime(String name, ActionMetadata metadata) {
        return metadata.runtime().map(runtime -> ownRuntime(name, runtime)).orElseGet(
            () -> Stream.of(
                new Offence(
                    false, new Refusal("runs", "the action declares no runs.using", "runs.using naming " + RUNTIMES)
                        .at(name)
                )
            )
        );
    }

    private static Stream<Offence> ownRuntime(String name, Located<String> runtime) {
        String location = name + LINE_SEPARATOR + runtime.line();
        String written = runtime.value();
        Optional<Integer> node = ActionMetadata.nodeVersion(written);
        if (node.isPresent()) {
            return node.filter(major -> major < REQUIRED_RUNTIME).stream().map(
                _ -> new Offence(
                    true, new Refusal(written, "the runtime this action declares" + DEPRECATED, REQUIRED + " or later")
                        .at(location)
                )
            );
        }
        boolean known = COMPOSITE.equals(written) || DOCKER.equals(written);
        return known ? Stream.empty() : Stream.of(
            new Offence(false, new Refusal(written, "not a runtime GitHub runs actions on", RUNTIMES).at(location))
        );
    }

    private Stream<Offence> judged(String location, String written) {
        return switch (ActionUses.read(written)) {
            case ActionUse.Skipped _ -> Stream.empty();
            case ActionUse.Unjudgeable unjudgeable -> Stream.of(
                new Offence(false, new Refusal(written, unjudgeable.reason(), unjudgeable.replacement()).at(location))
            );
            case ActionReference action -> this.problems(action, List.of()).stream()
                .distinct()
                .map(problem -> new Offence(problem.outdated(), problem.against(action).at(location)));
            case null -> throw new IllegalStateException("No reading of the uses value " + written);
        };
    }

    // Every way the action, or an action a composite reaches, breaks the rule. The ancestors are the composite
    // actions on the way to it, so a composite that reaches itself again is followed no further.
    private List<Problem> problems(ActionReference action, List<ActionReference> ancestors) {
        if (ancestors.contains(action)) {
            return List.of();
        }
        return ancestors.size() >= NESTING_LIMIT ? List.of(
            Problem.unread(
                action, "nested deeper than the " + NESTING_LIMIT + " composite actions GitHub runs",
                "a release of the outermost action that nests fewer"
            )
        ) : this.fetchedProblems(action, ancestors);
    }

    private List<Problem> fetchedProblems(ActionReference action, List<ActionReference> ancestors) {
        return this.fetched.computeIfAbsent(action, this.source::metadata)
            .map(text -> this.metadataProblems(action, ActionMetadata.read(text), ancestors))
            .orElseGet(
                () -> List.of(
                    Problem.unread(
                        action,
                        "neither action.yml nor action.yaml is at that ref, so the repository, the path or the ref"
                            + " does not exist, or reading it needs a credential",
                        "a ref of " + action.slug() + " that holds the action"
                    )
                )
            );
    }

    private List<Problem> metadataProblems(
        ActionReference action, ActionMetadata metadata, List<ActionReference> ancestors
    ) {
        return metadata.runtime()
            .map(runtime -> this.runtimeProblems(action, runtime.value(), metadata, ancestors))
            .orElseGet(
                () -> List.of(
                    Problem.unread(
                        action, "its metadata declares no runs.using", "a release of " + action.slug() + " that does"
                    )
                )
            );
    }

    private List<Problem> runtimeProblems(
        ActionReference action, String runtime, ActionMetadata metadata, List<ActionReference> ancestors
    ) {
        Optional<Integer> node = ActionMetadata.nodeVersion(runtime);
        if (node.isPresent()) {
            return node.filter(major -> major < REQUIRED_RUNTIME).map(_ -> List.of(Problem.outdated(action, runtime)))
                .orElseGet(List::of);
        }
        return DOCKER.equals(runtime) ? List.of() : this.otherProblems(action, runtime, metadata, ancestors);
    }

    // A runtime that is neither Node nor Docker: a composite, whose steps are judged in turn, or none GitHub runs.
    private List<Problem> otherProblems(
        ActionReference action, String runtime, ActionMetadata metadata, List<ActionReference> ancestors
    ) {
        if (!COMPOSITE.equals(runtime)) {
            return List.of(
                Problem.unread(
                    action, "it declares " + runtime + ", not a runtime GitHub runs actions on",
                    "a release of " + action.slug() + " that declares " + RUNTIMES
                )
            );
        }
        List<ActionReference> path = Stream.concat(ancestors.stream(), Stream.of(action)).toList();
        return metadata.steps().stream()
            .flatMap(step -> this.stepProblems(action, step.value(), path).stream())
            .toList();
    }

    private List<Problem> stepProblems(ActionReference composite, String written, List<ActionReference> path) {
        return switch (ActionUses.read(written)) {
            case ActionUse.Skipped _ -> List.of();
            case ActionUse.Unjudgeable unjudgeable -> List.of(
                Problem.unread(
                    composite, "a step uses " + written + ", " + unjudgeable.reason(),
                    "a release of " + composite.slug() + " whose steps can be read"
                )
            );
            case ActionReference inner -> this.problems(inner, path);
            case null -> throw new IllegalStateException("No reading of the step " + written);
        };
    }

    /**
     * One line of a verdict, and which verdict it belongs to.
     *
     * @param outdated whether it names a deprecated runtime rather than a runtime that cannot be read
     * @param text     the offence
     */
    private record Offence(boolean outdated, String text) {
    }

    /**
     * One way an action breaks the rule, found in the metadata of the action that declares it.
     *
     * @param culprit     the action whose metadata the problem is in
     * @param outdated    whether the problem is a deprecated runtime
     * @param reason      the deprecated runtime, or why the runtime cannot be read
     * @param replacement what to use instead, when the culprit is the action the workflow names
     */
    private record Problem(ActionReference culprit, boolean outdated, String reason, String replacement) {

        static Problem outdated(ActionReference culprit, String runtime) {
            return new Problem(
                culprit, true, runtime,
                "a commit of " + culprit.slug() + " whose metadata declares " + REQUIRED + " or later"
            );
        }

        static Problem unread(ActionReference culprit, String reason, String replacement) {
            return new Problem(culprit, false, reason, replacement);
        }

        // The refusal of the action the workflow names. A problem found deeper is the named action's to repair, by
        // a release that no longer reaches the culprit, so the replacement is a release of the named action.
        Refusal against(ActionReference named) {
            String subject = named.toString();
            if (this.culprit.equals(named)) {
                String complaint = this.outdated ? "declares " + this.reason + DEPRECATED : this.reason;
                return new Refusal(subject, complaint, this.replacement);
            }
            String reached = "runs " + this.culprit + ", which ";
            return this.outdated
                ? new Refusal(
                    subject, reached + "declares " + this.reason + DEPRECATED,
                    "a release of " + named.slug() + " whose actions all declare " + REQUIRED + " or later"
                )
                : new Refusal(
                    subject, reached + "cannot be read: " + this.reason,
                    "a release of " + named.slug() + " whose actions can all be read"
                );
        }
    }
}
