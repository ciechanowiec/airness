package eu.ciechanowiec.airness.governance;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * The refusal rule: whether an image reference, a coordinate, a system package, a JDK distribution, or
 * the JDK running the build names something in {@link BlocklistEntries}.
 *
 * <p>An image is matched on its repository after normalisation, because the same image is written four
 * ways and a list keyed on the spelling would refuse one of them and pull the other. The tag decides
 * only where an entry carries a floor: a tag below the floor is the open release line and is left to the
 * licence allowlist, and a tag that cannot be placed against the floor, such as {@code latest}, none,
 * {@code 7} against a floor of {@code 7.4}, or an {@code alpine} suffix on nothing numeric, is refused,
 * because a tag that may resolve past the floor tomorrow is not a proof that it sits below it today.
 *
 * <p>A reference nothing pins is refused on its own account, whatever it names: with no tag, or the
 * {@code latest} tag, what it pulls changes without the repository changing. And a reference still
 * carrying a variable is refused as one nothing can judge, rather than passed on a guess about what the
 * variable holds.
 */
@UtilityClass
final class Blocklist {

    private static final Pattern NUMERIC_PREFIX = Pattern.compile("^v?(?<version>\\d+(?:\\.\\d+)*)");
    private static final String OPEN_RUNTIME = "OpenJDK";
    private static final String UNNAMED_RUNTIME = "an unnamed runtime";
    private static final String UNPINNED
        = "nothing pins what this pulls, so it can change without the repository changing";
    private static final String PIN = "a tag, or a tag and a digest";
    private static final String UNRESOLVED = "a variable nothing substituted is still in it, so it cannot be judged";
    private static final String LITERAL = "the reference written literally, or a variable written with a default";
    private static final String CLOSED_RUNTIME
        = "the JDK running this build is not an OpenJDK build, and Oracle JDK and Oracle GraalVM are not open source";
    private static final String OPEN_BUILD
        = "an OpenJDK build such as Eclipse Temurin at the version .java-version pins";
    private static final String COMPONENT = "[.]";

    /**
     * Everything the rule has to say about one image reference: that it cannot be judged, that it is
     * refused, or that nothing pins it, in the order a reader repairs them.
     *
     * @param raw the reference as written
     * @return the refusal, or nothing when the reference is allowed and pinned
     */
    static Optional<Refusal> judgeImage(String raw) {
        return unresolved(raw).or(() -> image(raw)).or(() -> unpinned(raw));
    }

    /**
     * Whether an image reference names a refused repository at a refused tag.
     *
     * @param raw the reference as written
     * @return the refusal, or nothing when the image is not one Airness refuses
     */
    static Optional<Refusal> image(String raw) {
        ImageReference reference = ImageReference.parse(raw);
        return BlocklistEntries.image(reference.repository())
            .filter(entry -> refuses(entry, reference.tag()))
            .map(entry -> new Refusal(raw, entry.reason(), entry.replacement()));
    }

    /**
     * Whether nothing in an image reference pins what is pulled.
     *
     * @param raw the reference as written
     * @return the refusal, or nothing when a tag or a digest pins it
     */
    static Optional<Refusal> unpinned(String raw) {
        return Optional.of(raw)
            .filter(reference -> ImageReference.parse(reference).mutable())
            .map(reference -> new Refusal(reference, UNPINNED, PIN));
    }

    /**
     * Whether an image reference still carries a variable, so no rule can say what it pulls.
     *
     * @param raw the reference as written
     * @return the refusal, or nothing when the reference is literal
     */
    static Optional<Refusal> unresolved(String raw) {
        return Optional.of(raw)
            .filter(reference -> ImageReference.parse(reference).unresolved())
            .map(reference -> new Refusal(reference, UNRESOLVED, LITERAL));
    }

    /**
     * Whether a coordinate at a version is one Airness refuses.
     *
     * @param declared the coordinate, with the version as declared or resolved
     * @return the refusal, or nothing when the coordinate is not refused at that version
     */
    static Optional<Refusal> coordinate(DeclaredCoordinate declared) {
        String subject = declared.groupId() + ':' + declared.artifactId() + ':' + declared.version();
        return BlocklistEntries.coordinate(declared.groupId(), declared.artifactId())
            .filter(entry -> entry.floor().map(floor -> reachesFloor(declared.version(), floor)).orElse(true))
            .map(entry -> new Refusal(subject, entry.reason(), entry.replacement()));
    }

    /**
     * Whether a system package a Dockerfile installs is one Airness refuses.
     *
     * @param name the package name as the install line writes it
     * @return the refusal, or nothing when the package is not refused
     */
    static Optional<Refusal> systemPackage(String name) {
        return BlocklistEntries.systemPackage(name)
            .map(entry -> new Refusal(name, entry.reason(), entry.replacement()));
    }

    /**
     * Whether a JDK distribution a workflow installs is one Airness refuses.
     *
     * @param name the distribution as the workflow writes it
     * @return the refusal, or nothing when the distribution is an open build
     */
    static Optional<Refusal> distribution(String name) {
        return BlocklistEntries.distribution(name.toLowerCase(Locale.ROOT))
            .map(entry -> new Refusal(name, entry.reason(), entry.replacement()));
    }

    /**
     * Whether a JDK vendor a {@code .sdkmanrc} selects is one Airness refuses.
     *
     * @param candidate the vendor suffix of the java entry, such as {@code tem} or {@code oracle}
     * @return the refusal, or nothing when the vendor ships an open build
     */
    static Optional<Refusal> sdkmanVendor(String candidate) {
        return BlocklistEntries.sdkmanVendor(candidate.toLowerCase(Locale.ROOT))
            .map(entry -> new Refusal(candidate, entry.reason(), entry.replacement()));
    }

    /**
     * Whether the JDK running the build is an open build. The name every open build reports starts with
     * {@code OpenJDK}, and the one Oracle's own reports starts with {@code Java(TM) SE}, so the name is
     * the one fact that tells them apart. A name the rule cannot read is refused too, since it is not a
     * proof of an open build.
     *
     * @param runtimeName the {@code java.runtime.name} system property
     * @return the refusal, or nothing when the runtime is an OpenJDK build
     */
    static Optional<Refusal> runtime(String runtimeName) {
        return Optional.of(runtimeName)
            .filter(name -> !name.startsWith(OPEN_RUNTIME))
            .map(name -> new Refusal(name.isEmpty() ? UNNAMED_RUNTIME : name, CLOSED_RUNTIME, OPEN_BUILD));
    }

    // An allowed tag wins outright. Otherwise a refused tag pattern names the only tags refused, and
    // failing that the floor decides, and an entry with neither refuses every tag.
    private static boolean refuses(BlockedImage entry, Optional<String> tag) {
        boolean exempt = entry.allowedTag().map(pattern -> matchesTag(pattern, tag)).orElse(false);
        boolean refused = entry.refusedTag()
            .map(pattern -> matchesTag(pattern, tag))
            .orElseGet(() -> entry.floor().map(floor -> reachesTagFloor(tag, floor)).orElse(true));
        return !exempt && refused;
    }

    private static boolean matchesTag(Pattern pattern, Optional<String> tag) {
        return tag.filter(held -> pattern.matcher(held).find()).isPresent();
    }

    // Undecidable is refused: a tag that cannot be placed against the floor is not one known to sit
    // below it. A tag with fewer numeric components than the floor floats, since 7 resolves to whatever
    // 7.x is newest, and a tag with no numeric prefix says nothing about its version at all.
    private static boolean reachesTagFloor(Optional<String> tag, String floor) {
        return tag.flatMap(Blocklist::numericPrefix)
            .filter(version -> components(version) >= components(floor))
            .map(version -> reachesFloor(version, floor))
            .orElse(true);
    }

    private static boolean reachesFloor(String version, String floor) {
        return numericPrefix(version)
            .map(prefix -> new ComparableVersion(prefix).compareTo(new ComparableVersion(floor)) >= 0)
            .orElse(true);
    }

    private static Optional<String> numericPrefix(String version) {
        Matcher matched = NUMERIC_PREFIX.matcher(version);
        return matched.find() ? Optional.of(matched.group("version")) : Optional.empty();
    }

    private static int components(String version) {
        return version.split(COMPONENT).length;
    }
}
