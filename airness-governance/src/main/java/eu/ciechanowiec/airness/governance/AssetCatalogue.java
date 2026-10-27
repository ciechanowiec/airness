package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The files the harness owns and the bytes it ships for each, read off a classloader.
 *
 * <p>Each capability has a manifest of its own, {@code airness/manifest/<capability>.tsv}, and ships its
 * bodies under {@code airness/files/<capability>/}. A row names a path, a policy, optionally a location
 * ({@code ROOT} unless stated), and optionally {@code OVERRIDES <capability>}. The generic manifest is
 * required. The others are read when the classpath carries them, which is how a layer published from
 * another repository adds its files. A body placed in each node project is shipped under
 * {@code airness/project-files/<capability>/} instead, so one path can read differently at the root and
 * inside a node project.
 *
 * <p>A manifest deliberately carries no checksum: a checksum in the manifest would be a second spelling
 * of what the shipped bytes already say, and the two would eventually disagree, at which point the
 * manifest is believed and the bytes are not. Hashing the shipped entry at the moment of comparison
 * leaves one source of truth and nothing to regenerate.
 *
 * <p>Two capabilities claiming one whole file is refused when the catalogue is read, unless the more
 * specific one names the other with {@code OVERRIDES}. Who owns a file is therefore decided by the harness
 * that ships the files, and a disagreement is a harness bug its own tests catch rather than a finding a
 * consumer meets.
 *
 * <p>A canonical file is stored under an added {@code .asset} suffix. Without it, the copy of
 * {@code .gitignore} this jar ships would be a working {@code .gitignore} over its own source directory
 * and could stop the other canonical files from being tracked, and the copy of {@code .gitattributes}
 * would take effect over them. A file that changes the meaning of the directory it is stored in cannot
 * be stored under its own name.
 */
public final class AssetCatalogue {

    private static final String MANIFESTS = "airness/manifest/";
    private static final String TSV = ".tsv";
    private static final String FILES = "airness/files/";
    private static final String PROJECT_FILES = "airness/project-files/";
    private static final String SUFFIX = ".asset";
    private static final String COMMENT = "#";
    private static final String SEPARATOR = "\t";
    private static final String OVERRIDES = "OVERRIDES ";
    private static final int REQUIRED_FIELDS = 2;
    private static final int LOCATION_FIELD = 2;
    private static final int OVERRIDES_FIELD = 3;
    private static final int MOST_FIELDS = 4;

    private final ClassLoader classes;
    private final List<Row> rows;

    /**
     * Reads every capability manifest off the given classloader and refuses one that contradicts another.
     *
     * @param classes the classloader carrying the assets artifact
     * @throws IllegalStateException when the generic manifest is missing, a row is malformed, or two
     *                               capabilities claim one file without an override that resolves it
     */
    public AssetCatalogue(ClassLoader classes) {
        this.classes = classes;
        if (this.manifest(Capability.GENERIC).isEmpty()) {
            throw new IllegalStateException(
                "No " + MANIFESTS + Capability.GENERIC.id() + TSV
                    + " on the classpath, so nothing states which files the harness owns"
            );
        }
        this.rows = Arrays.stream(Capability.values())
            .flatMap(capability -> this.manifest(capability).stream().flatMap(text -> parse(capability, text)))
            .toList();
        validate(this.rows);
    }

    /**
     * Every file every capability owns.
     *
     * @return the managed assets, in capability order and then manifest order
     */
    public List<ManagedAsset> assets() {
        return this.rows.stream().map(Row::asset).toList();
    }

    /**
     * The files placed at one kind of location where the given capabilities are present, after every
     * override a present capability declares has replaced the file it names.
     *
     * @param location the kind of location
     * @param present  the capabilities present there
     * @return the managed assets that apply, in capability order and then manifest order
     */
    public List<ManagedAsset> select(AssetLocation location, Collection<Capability> present) {
        List<Row> applicable = this.rows.stream()
            .filter(row -> row.asset().location() == location && present.contains(row.asset().owner()))
            .toList();
        return applicable.stream()
            .filter(row -> applicable.stream().noneMatch(other -> other.replaces(row)))
            .map(Row::asset)
            .toList();
    }

    /**
     * The bytes the harness ships for a managed file: a whole file, or the body of one block.
     *
     * @param asset the managed file
     * @return the canonical bytes, or nothing when the harness ships none for it
     */
    Optional<byte[]> canonical(ManagedAsset asset) {
        return this.read(resource(asset));
    }

    static String resource(ManagedAsset asset) {
        String base = asset.location() == AssetLocation.ROOT ? FILES : PROJECT_FILES;
        // Scaffolds can target development-control directories. Their flat, encoded resource names keep
        // those directories out of the production archive while preserving the target path in the manifest.
        String path = asset.policy() == AssetPolicy.SCAFFOLD
            ? URLEncoder.encode(asset.path(), StandardCharsets.UTF_8) : asset.path();
        return base + asset.owner().id() + '/' + path + SUFFIX;
    }

    private Optional<String> manifest(Capability capability) {
        return this.read(MANIFESTS + capability.id() + TSV).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    private Optional<byte[]> read(String resource) {
        try (InputStream stream = this.classes.getResourceAsStream(resource)) {
            return Optional.ofNullable(stream).map(AssetCatalogue::drain);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + resource, exception);
        }
    }

    private static byte[] drain(InputStream stream) {
        try {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read a shipped asset", exception);
        }
    }

    private static Stream<Row> parse(Capability owner, String manifest) {
        return manifest.lines()
            .map(String::strip)
            .filter(line -> !line.isEmpty() && !line.startsWith(COMMENT))
            .map(line -> entry(owner, line));
    }

    private static Row entry(Capability owner, String line) {
        String[] fields = line.split(SEPARATOR, -1);
        if (fields.length < REQUIRED_FIELDS || fields.length > MOST_FIELDS) {
            throw new IllegalStateException(
                "A manifest line needs a path and a policy, then optionally a location and an override: " + line
            );
        }
        AssetLocation location = fields.length > LOCATION_FIELD
            ? AssetLocation.valueOf(fields[LOCATION_FIELD].strip())
            : AssetLocation.ROOT;
        Set<Capability> overrides = fields.length > OVERRIDES_FIELD
            ? Set.of(overridden(fields[OVERRIDES_FIELD].strip(), line))
            : Set.of();
        ManagedAsset asset = new ManagedAsset(
            fields[0].strip(), AssetPolicy.valueOf(fields[1].strip().toUpperCase(Locale.ROOT)), owner, location
        );
        return new Row(asset, overrides);
    }

    private static Capability overridden(String field, String line) {
        if (!field.startsWith(OVERRIDES)) {
            throw new IllegalStateException("A manifest line's fourth field is OVERRIDES <capability>: " + line);
        }
        return Capability.of(field.substring(OVERRIDES.length()).strip());
    }

    private static void validate(Collection<Row> rows) {
        rows.stream()
            .collect(Collectors.groupingBy(Row::key, Collectors.toList()))
            .forEach(AssetCatalogue::validate);
    }

    private static void validate(String key, List<Row> claims) {
        validateOwners(key, claims);
        if (claims.stream().allMatch(Row::section)) {
            validateSections(key, claims);
        } else {
            validateWholeFile(key, claims);
        }
    }

    private static void validateOwners(String key, Collection<Row> claims) {
        Map<Capability, Long> owners = claims.stream()
            .collect(Collectors.groupingBy(row -> row.asset().owner(), Collectors.counting()));
        if (owners.values().stream().anyMatch(count -> count > 1)) {
            refuse(key + " is claimed twice by one capability");
        }
        if (claims.stream().anyMatch(Row::section) && !claims.stream().allMatch(Row::section)) {
            refuse(key + " is claimed both as a shared block and as a whole file");
        }
    }

    private static void validateSections(String key, Collection<Row> claims) {
        claims.stream().filter(row -> !row.overrides().isEmpty()).findFirst().ifPresent(
            row -> refuse(key + " is shared through blocks, so " + row.asset().owner().id() + " cannot override it")
        );
    }

    private static void validateWholeFile(String key, List<Row> claims) {
        Map<Capability, Row> byOwner = claims.stream()
            .collect(Collectors.toMap(row -> row.asset().owner(), Function.identity()));
        claims.forEach(row -> row.overrides().forEach(named -> validateOverride(key, row, named, byOwner)));
        long bases = claims.stream().filter(row -> row.overrides().isEmpty()).count();
        if (bases != 1) {
            refuse(
                key + " is claimed by " + byOwner.keySet().stream().map(Capability::id).sorted().toList()
                    + " without exactly one owner and an OVERRIDES on every other claim"
            );
        }
    }

    private static void validateOverride(String key, Row row, Capability named, Map<Capability, Row> byOwner) {
        Capability owner = row.asset().owner();
        if (!byOwner.containsKey(named)) {
            refuse(key + ": " + owner.id() + " overrides " + named.id() + ", which does not own it");
        }
        if (!owner.refines(named)) {
            refuse(
                key + ": " + owner.id() + " is not a more specific form of " + named.id() + ", so it cannot override it"
            );
        }
    }

    private static void refuse(String problem) {
        throw new IllegalStateException("The harness manifests contradict each other: " + problem);
    }

    /**
     * One manifest row: the file it claims and the capability it replaces, when it replaces one.
     *
     * @param asset     the claimed file
     * @param overrides the capability whose claim this row replaces, or nothing
     */
    private record Row(ManagedAsset asset, Set<Capability> overrides) {

        private String key() {
            return this.asset.path() + " at " + this.asset.location();
        }

        private boolean section() {
            return this.asset.policy() == AssetPolicy.SECTION;
        }

        private boolean replaces(Row other) {
            return this.key().equals(other.key()) && this.overrides.contains(other.asset.owner());
        }
    }
}
