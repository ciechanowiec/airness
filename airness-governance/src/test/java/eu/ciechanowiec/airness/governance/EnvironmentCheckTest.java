package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a compose file's variables must reach the example file and every verifying workflow.
 */
class EnvironmentCheckTest {

    private static final String NEXT = "next";
    private static final String EMPTY_MANIFEST = "{}\n";
    private static final String NEXT_CONFIG
        = "const a = process.env.NEXT_PUBLIC_SITE\nconst b = process.env['NEXT_PUBLIC_KEY']\n";

    private static final String COMPOSE = """
        services:
          db:
            image: postgres:17.5
            environment:
              POSTGRES_PASSWORD: ${DB_PASSWORD}
              POSTGRES_USER: ${DB_USER}
              POSTGRES_PORT: ${DB_PORT:-5432}
        """;
    private static final String VERIFYING = """
        on: push
        jobs:
          verify:
            runs-on: ubuntu-latest
            env:
              DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
            steps:
              - run: mvn --batch-mode verify
        """;

    // Every package of the fixture is a Next.js application.
    private static List<String> undeclared(Path root) {
        EnvironmentCheck check = new EnvironmentCheck(
            root, _ -> Optional.of(new PackageManifest(Set.of(), Set.of(NEXT), Set.of(NEXT)))
        );
        return Verdicts.offences(check.findings(), "a Dockerfile building it");
    }

    @Test
    void namesEveryVariableTheExampleFileDoesNotDocument() {
        Path root = new GitFixture("environment-undocumented")
            .write("deploy/compose.yaml", COMPOSE)
            .write(".env.example", "DB_PASSWORD=\n")
            .write(".env.sample", "DB_USER=\n")
            .root();
        assertEquals(
            List.of("DB_USER in " + Path.of("deploy", "compose.yaml")),
            Verdicts.offences(new EnvironmentCheck(root).findings(), "no example file documents"),
            "only the first example file is read, and a variable with a default needs no documentation"
        );
    }

    @Test
    void namesEveryVariableAVerifyingWorkflowDoesNotSupply() {
        Path root = new GitFixture("environment-unsupplied")
            .write("compose.yaml", COMPOSE)
            .write(".env.example", "DB_PASSWORD=\nDB_USER=\n")
            .write(".github/workflows/verify.yml", VERIFYING)
            .write(".github/workflows/label.yml", "on: issues\njobs: {}\n")
            .root();
        assertEquals(
            List.of("DB_USER in " + Path.of(".github", "workflows", "verify.yml")),
            Verdicts.offences(new EnvironmentCheck(root).findings(), "verifying workflow does not supply"),
            "a workflow that runs verification owes every compose variable, and one that does not owes none"
        );
    }

    @Test
    void reportsEveryVariableWhenThereIsNoExampleFile() {
        Path root = new GitFixture("environment-no-example").write("compose.yaml", COMPOSE).root();
        assertEquals(
            List.of("DB_PASSWORD in compose.yaml", "DB_USER in compose.yaml"),
            Verdicts.offences(new EnvironmentCheck(root).findings(), "no example file documents"),
            "a repository with no example file documents nothing"
        );
    }

    @Test
    void passesARepositoryWithNoComposeFile() {
        Path root = new GitFixture("environment-none").write(".github/workflows/verify.yml", VERIFYING).root();
        assertTrue(Verdicts.clean(new EnvironmentCheck(root).findings()), "nothing interpolates, so nothing is owed");
    }

    @Test
    void namesEveryVariableANextBuildInlinesThatTheDockerfileBuildingItDoesNotDeclare() {
        Path root = new GitFixture("environment-next")
            .write("web/package.json", EMPTY_MANIFEST)
            .write("web/next.config.ts", NEXT_CONFIG)
            .write("web/Dockerfile", "FROM node:26\nARG NEXT_PUBLIC_SITE=x \\\n    OTHER\nRUN build\n")
            .write("api/package.json", EMPTY_MANIFEST)
            .write("api/next.config.ts", "process.env.NEXT_PUBLIC_ELSEWHERE\n")
            .write("Dockerfile", "FROM node:26\n")
            .root();
        Function<Path, Optional<PackageManifest>> manifests = _ -> Optional.of(
            new PackageManifest(Set.of(), Set.of(NEXT), Set.of(NEXT))
        );
        assertEquals(
            List.of("NEXT_PUBLIC_KEY in " + Path.of("web", "Dockerfile")),
            Verdicts.offences(new EnvironmentCheck(root, manifests).findings(), "a Dockerfile building it"),
            "a declaration with a default declares the name, a Next.js package with no Dockerfile of its own owes"
                + " nothing, and the root Dockerfile builds no package, since the root holds no package.json"
        );
    }

    @Test
    void readsNoNodePackageWithoutAManifestReader() {
        Path root = new GitFixture("environment-next-unread")
            .write("package.json", EMPTY_MANIFEST)
            .write("next.config.ts", "process.env.NEXT_PUBLIC_SITE\n")
            .write("Dockerfile", "FROM node:26\n")
            .root();
        assertTrue(Verdicts.clean(new EnvironmentCheck(root).findings()), "no package is known to be Next.js");
    }

    @Test
    void readsADockerfileOutsideTheApplicationThatNamesItsDirectory() {
        Path root = new GitFixture("environment-next-root-dockerfile")
            .write("apps/web/package.json", EMPTY_MANIFEST)
            .write("apps/web/next.config.ts", NEXT_CONFIG)
            .write("apps/admin/package.json", EMPTY_MANIFEST)
            .write("apps/admin/next.config.ts", "process.env.NEXT_PUBLIC_ADMIN\n")
            .write("docker/web.Dockerfile", "FROM node:26\nARG NEXT_PUBLIC_SITE\nCOPY apps/web /app\n")
            .write("Dockerfile", "FROM node:26\nCOPY . /src\n")
            .root();
        Function<Path, Optional<PackageManifest>> manifests = _ -> Optional.of(
            new PackageManifest(Set.of(), Set.of(NEXT), Set.of(NEXT))
        );
        assertEquals(
            List.of("NEXT_PUBLIC_KEY in " + Path.of("docker", "web.Dockerfile")),
            Verdicts.offences(new EnvironmentCheck(root, manifests).findings(), "a Dockerfile building it"),
            "a Dockerfile no package holds builds the application whose directory it names, and only that one"
        );
    }

    @Test
    void readsNoDockerfileThatOnlyContainsTheDirectoryNameInsideAnotherWord() {
        Path root = new GitFixture("environment-next-substring")
            .write("ui/package.json", EMPTY_MANIFEST)
            .write("ui/next.config.ts", NEXT_CONFIG)
            .write("app/package.json", EMPTY_MANIFEST)
            .write("app/next.config.ts", NEXT_CONFIG)
            .write("web/package.json", EMPTY_MANIFEST)
            .write("web/next.config.ts", NEXT_CONFIG)
            .write("services/api/Dockerfile", "FROM golang:1.25\nRUN make build\n")
            .write("services/worker/Dockerfile", "FROM node:26\nWORKDIR /app\nCOPY . /app\n")
            .write("services/site/Dockerfile", "FROM nginx:1.29\nCOPY website /usr/share/web\n")
            .root();
        assertTrue(
            undeclared(root).isEmpty(),
            "build is not ui, /app is a path inside the image, and website is not web"
        );
    }

    @Test
    void readsNoDockerfileThatOnlyCopiesTheManifestOrCopiesIntoTheDirectoryName() {
        Path root = new GitFixture("environment-next-workspace-member")
            .write("web/package.json", EMPTY_MANIFEST)
            .write("web/next.config.ts", NEXT_CONFIG)
            .write("api/package.json", EMPTY_MANIFEST)
            .write(
                "api/Dockerfile", """
                    FROM node:26
                    # The workspace needs web/package.json beside this member's.
                    COPY package.json pnpm-lock.yaml ./
                    COPY web/package.json web/
                    COPY ["api/package.json", "api/"]
                    RUN pnpm install --filter api
                    """
            ).write("docker/assets.Dockerfile", "FROM nginx:1.29\nCOPY assets web/static\n")
            .root();
        assertTrue(
            undeclared(root).isEmpty(),
            "a manifest copied for the workspace, a copy's destination and a comment build no application"
        );
    }

    @Test
    void readsEverySpellingOfARelativePathNamingTheApplicationDirectory() {
        Path root = new GitFixture("environment-next-spellings")
            .write("web/package.json", EMPTY_MANIFEST)
            .write("web/next.config.ts", NEXT_CONFIG)
            .write("docker/a-plain.Dockerfile", "FROM node:26\nCOPY web /app\n")
            .write("docker/b-current.Dockerfile", "FROM node:26\nCOPY ./web/ ./\n")
            .write("docker/c-exec.Dockerfile", "FROM node:26\nCOPY [\"web\", \"/app\"]\n")
            .write("docker/d-quoted.Dockerfile", "FROM node:26\nCOPY 'web/next.config.ts' /app/\n")
            .write("docker/e-workspace.Dockerfile", "FROM node:26\nRUN npm run build --workspace=web\n")
            .write("docker/f-quoted-option.Dockerfile", "FROM node:26\nRUN npm run build --workspace=\"web\"\n")
            .write("docker/g-separate.Dockerfile", "FROM node:26\nRUN cd web&&npm run build -w web\n")
            .root();
        List<String> expected = Stream.of(
            "a-plain", "b-current", "c-exec", "d-quoted", "e-workspace", "f-quoted-option", "g-separate"
        ).flatMap(
            name -> Stream.of("NEXT_PUBLIC_KEY", "NEXT_PUBLIC_SITE")
                .map(variable -> variable + " in " + Path.of("docker", name + ".Dockerfile"))
        ).toList();
        assertEquals(
            expected, undeclared(root),
            "each spelling of the application directory names it"
        );
    }

    @Test
    void judgesEachDockerfileBuildingTheApplicationByItsOwnDeclarations() {
        Path root = new GitFixture("environment-next-each")
            .write("web/package.json", EMPTY_MANIFEST)
            .write("web/next.config.ts", NEXT_CONFIG)
            .write("web/Dockerfile", "FROM node:26\nARG NEXT_PUBLIC_SITE\nARG NEXT_PUBLIC_KEY\n")
            .write("web/Dockerfile.e2e", "FROM node:26\nRUN build\n")
            .write("web/preview.Dockerfile", "FROM node:26\nARG NEXT_PUBLIC_KEY\n")
            .root();
        Path endToEnd = Path.of("web", "Dockerfile.e2e");
        assertEquals(
            List.of(
                "NEXT_PUBLIC_KEY in " + endToEnd,
                "NEXT_PUBLIC_SITE in " + endToEnd,
                "NEXT_PUBLIC_SITE in " + Path.of("web", "preview.Dockerfile")
            ),
            undeclared(root),
            "docker drops a build argument the Dockerfile being built never declared, whatever its siblings declare"
        );
    }
}
