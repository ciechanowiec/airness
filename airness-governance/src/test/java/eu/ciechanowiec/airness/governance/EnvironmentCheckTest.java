package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a compose file's variables must reach the example file and every verifying workflow.
 */
class EnvironmentCheckTest {

    private static final String NEXT = "next";
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
            .write("web/package.json", "{}\n")
            .write("web/next.config.ts", NEXT_CONFIG)
            .write("web/Dockerfile", "FROM node:26\nARG NEXT_PUBLIC_SITE=x \\\n    OTHER\nRUN build\n")
            .write("api/package.json", "{}\n")
            .write("api/next.config.ts", "process.env.NEXT_PUBLIC_ELSEWHERE\n")
            .write("Dockerfile", "FROM node:26\n")
            .root();
        Function<Path, Optional<PackageManifest>> manifests = _ -> Optional.of(
            new PackageManifest(Set.of(), Set.of(NEXT))
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
            .write("package.json", "{}\n")
            .write("next.config.ts", "process.env.NEXT_PUBLIC_SITE\n")
            .write("Dockerfile", "FROM node:26\n")
            .root();
        assertTrue(Verdicts.clean(new EnvironmentCheck(root).findings()), "no package is known to be Next.js");
    }

    @Test
    void readsADockerfileOutsideTheApplicationThatNamesItsDirectory() {
        Path root = new GitFixture("environment-next-root-dockerfile")
            .write("apps/web/package.json", "{}\n")
            .write("apps/web/next.config.ts", NEXT_CONFIG)
            .write("apps/admin/package.json", "{}\n")
            .write("apps/admin/next.config.ts", "process.env.NEXT_PUBLIC_ADMIN\n")
            .write("docker/web.Dockerfile", "FROM node:26\nARG NEXT_PUBLIC_SITE\nCOPY apps/web /app\n")
            .write("Dockerfile", "FROM node:26\nCOPY . /src\n")
            .root();
        Function<Path, Optional<PackageManifest>> manifests = _ -> Optional.of(
            new PackageManifest(Set.of(), Set.of(NEXT))
        );
        assertEquals(
            List.of("NEXT_PUBLIC_KEY in " + Path.of("docker", "web.Dockerfile")),
            Verdicts.offences(new EnvironmentCheck(root, manifests).findings(), "a Dockerfile building it"),
            "a Dockerfile no package holds builds the application whose directory it names, and only that one"
        );
    }
}
