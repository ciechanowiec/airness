package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 * Which files of a repository a check reads is Git's answer, and the repository reader asks for it. No production
 * class of the plugin or of the governance library lists a directory of its own accord, except where this guard
 * names the class and the directory it lists, none of which is the repository.
 *
 * <p>A walk of the repository root reads what Git ignores: build output, a scratch checkout, a worktree nested in
 * the clone, a directory a container owns and nobody else may open. Each of those once failed a build, so a new
 * directory listing, recursive or not and however it is spelled, fails here until it is listed with the reason it
 * reads something other than the repository. An entry whose class no longer lists a directory fails too, so the
 * list never outlives the code it excuses.
 */
class RepositoryWalkTest {

    private static final Pattern LISTING = Pattern.compile(
        "\\bFiles\\s*(?:\\.|::)\\s*(?:walk|walkFileTree|find|list|newDirectoryStream)\\b"
            + "|import\\s+static\\s+java\\.nio\\.file\\.Files\\.(?:\\*|walk|walkFileTree|find|list|newDirectoryStream)"
            + "\\s*;|\\bDirectoryScanner\\b|\\.listFiles\\s*\\(|\\bFileUtils\\s*\\.\\s*(?:get|list)File"
    );
    private static final List<String> SOURCE_ROOTS = List.of(
        "airness-maven-plugin/src/main/java/eu/ciechanowiec/airness/maven/",
        "airness-governance/src/main/java/eu/ciechanowiec/airness/governance/"
    );
    private static final String PLUGIN = SOURCE_ROOTS.getFirst();
    private static final String GOVERNANCE = SOURCE_ROOTS.getLast();
    private static final String SOURCE_ROOTS_OF_A_MODULE = "a module's Java source roots";
    private static final String GIT_FALLBACK = ", and only where Git cannot list it";

    /**
     * Each class that lists a directory, with the directory it lists.
     */
    private static final Map<String, String> LISTINGS = Map.ofEntries(
        Map.entry(PLUGIN + "BuildPracticesMojo.java", "the workflow directory, for the workflow files at its top"),
        Map.entry(PLUGIN + "Closing.java", "one node project" + GIT_FALLBACK),
        Map.entry(PLUGIN + "InputSelection.java", "Maven's resource roots, through Maven's own file filters"),
        Map.entry(PLUGIN + "JavaParents.java", SOURCE_ROOTS_OF_A_MODULE),
        Map.entry(PLUGIN + "NewMojo.java", "the top of the directory a new application is written into"),
        Map.entry(PLUGIN + "NodeBuildInputs.java", "a node project's checked source roots, judged against Git"),
        Map.entry(PLUGIN + "NodeGeneratedArtefacts.java", "one node project, for its workspace members"),
        Map.entry(PLUGIN + "NodeManifests.java", "one module" + GIT_FALLBACK),
        Map.entry(PLUGIN + "PackageRoots.java", SOURCE_ROOTS_OF_A_MODULE),
        Map.entry(PLUGIN + "PinnedArchive.java", "a staging directory of an unpacked toolchain archive"),
        Map.entry(PLUGIN + "PluginScanFiles.java", "the plugin scan inputs it writes under the build directory"),
        Map.entry(PLUGIN + "ReportPublication.java", "the report directory, for earlier report pairs"),
        Map.entry(PLUGIN + "ScannerTree.java", "the scanner cache beneath the home directory and a staged tree in it"),
        Map.entry(PLUGIN + "SourceFormattingMojo.java", SOURCE_ROOTS_OF_A_MODULE),
        Map.entry(PLUGIN + "StarterCopy.java", "a starter project the plugin carries"),
        Map.entry(PLUGIN + "WebInterface.java", "a module's static web locations"),
        Map.entry(PLUGIN + "WebSweepLaunch.java", "the static location of a web module"),
        Map.entry(GOVERNANCE + "ArtifactContentCheck.java", "a module's build output, compared with its archive"),
        Map.entry(GOVERNANCE + "ParameterMetadataCheck.java", "a module's Java source and class directories"),
        Map.entry(GOVERNANCE + "ReportDestination.java", "the compliance report directory"),
        Map.entry(GOVERNANCE + "RootLicenseCheck.java", "the top of the root, for its license file, never below it")
    );

    @Test
    void listsADirectoryOnlyWhereTheGuardNamesIt() {
        Path repository = SelfModules.repository();
        List<String> listing = SOURCE_ROOTS.stream()
            .flatMap(root -> sources(repository, root))
            .filter(path -> LISTING.matcher(read(repository.resolve(path))).find())
            .sorted()
            .toList();
        assertTrue(listing.contains(PLUGIN + "ScannerTree.java"), "a guard that recognised nothing proves nothing");
        assertEquals(
            List.of(),
            listing.stream().filter(path -> !LISTINGS.containsKey(path)).toList(),
            "read the repository through Repository.committableFiles, as every reader of it does, or name the"
                + " directory this lists, which must not be the repository, in the guard"
        );
        assertEquals(
            List.of(),
            LISTINGS.keySet().stream().filter(path -> !listing.contains(path)).sorted().toList(),
            "an entry whose class no longer lists a directory is stale; remove it"
        );
    }

    @Test
    void recognisesEverySpellingOfADirectoryListing() {
        List.of(
            "try (Stream<Path> paths = Files.walk(root)) {", "Files.walkFileTree(root, visitor);",
            "Files.find(root, Integer.MAX_VALUE, (path, attributes) -> true)", "Files.list(directory)",
            "Files.newDirectoryStream(directory)", "java.nio.file.Files.walk(root)",
            "Stream.of(root).flatMap(Files::list)",
            "Files . walk (root)", "import static java.nio.file.Files.walk;", "import static java.nio.file.Files.*;",
            "new DirectoryScanner()", "root.toFile().listFiles()", "FileUtils.getFiles(root, \"**\", null)",
            "FileUtils.listFiles(root, null, true)"
        ).forEach(spelling -> assertTrue(LISTING.matcher(spelling).find(), spelling));
        List.of(
            "Files.lines(file)", "Files.readString(file)", "Repository.committableFiles(root)", "files.list()",
            "List.of(found)", "walker.walk(tree)"
        ).forEach(spelling -> assertFalse(LISTING.matcher(spelling).find(), spelling));
    }

    @SneakyThrows
    private static Stream<String> sources(Path repository, String root) {
        Path base = repository.resolve(root);
        try (Stream<Path> files = Files.walk(base)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".java"))
                .map(file -> root + base.relativize(file).toString().replace('\\', '/'))
                .toList()
                .stream();
        }
    }

    @SneakyThrows
    private static String read(Path file) {
        return Files.readString(file);
    }
}
