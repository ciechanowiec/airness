package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every hosted Ubuntu runner a CI file selects is named by the current long-term-support release, wherever the
 * label is written and however it is spelled.
 */
class RunnerImageCheckTest {

    private static final String HEADLINE = "Hosted Ubuntu runners";
    private static final String WORKFLOW = ".github/workflows/ci.yml";
    private static final String CURRENT = "ubuntu-" + RunnerImageCheck.CURRENT_RELEASE;
    private static final String ALIASED = " - an alias its provider moves to another release on its own schedule; use ";

    private static List<String> offences(Path root) {
        return Verdicts.offences(new RunnerImageCheck(root).findings(), HEADLINE);
    }

    private static List<String> workflowOffences(String name, String workflow) {
        return offences(new GitFixture(name).write(WORKFLOW, workflow).root());
    }

    private static String outdated(String location, String label, String release, String replacement) {
        return location + ": " + label + " - Ubuntu " + release
            + " rather than the current long-term-support release; use " + replacement;
    }

    @Test
    void refusesTheLatestAliasWithTheCurrentReleaseAsItsReplacement() {
        assertEquals(
            List.of(WORKFLOW + ":3: ubuntu-latest" + ALIASED + CURRENT),
            workflowOffences("runner-latest", "jobs:\n  build:\n    runs-on: ubuntu-latest\n")
        );
    }

    @Test
    void refusesAnEarlierReleaseAndKeepsTheArmSuffixInItsReplacement() {
        String workflow = """
            jobs:
              build:
                runs-on: ubuntu-24.04
              arm:
                runs-on: ubuntu-24.04-arm
              old:
                runs-on: ubuntu-22.04
            """;
        assertEquals(
            List.of(
                outdated(WORKFLOW + ":3", "ubuntu-24.04", "24.04", CURRENT),
                outdated(WORKFLOW + ":5", "ubuntu-24.04-arm", "24.04", CURRENT + "-arm"),
                outdated(WORKFLOW + ":7", "ubuntu-22.04", "22.04", CURRENT)
            ),
            workflowOffences("runner-releases", workflow)
        );
    }

    @Test
    void judgesALargerRunnerByTheReleaseItsLabelCarries() {
        assertEquals(
            List.of(outdated(WORKFLOW + ":3", "ubuntu-24.04-8-core", "24.04", CURRENT + "-8-core")),
            workflowOffences("runner-larger", "jobs:\n  build:\n    runs-on: ubuntu-24.04-8-core\n")
        );
    }

    @Test
    void readsEveryShapeARunsOnKeyHoldsALabelIn() {
        String workflow = """
            jobs:
              flow:
                runs-on: [ubuntu-24.04, ubuntu-latest]
              block:
                runs-on:
                  - "ubuntu-22.04"
              mapping:
                runs-on: { labels: 'ubuntu-24.04-arm' }
              labels:
                runs-on:
                  group: hosted
                  labels: [ubuntu-24.04]
            """;
        assertEquals(
            List.of(
                WORKFLOW + ":3: ubuntu-24.04",
                WORKFLOW + ":3: ubuntu-latest",
                WORKFLOW + ":6: ubuntu-22.04",
                WORKFLOW + ":8: ubuntu-24.04-arm",
                WORKFLOW + ":12: ubuntu-24.04"
            ),
            workflowOffences("runner-shapes", workflow).stream().map(RunnerImageCheckTest::subject).toList()
        );
    }

    @Test
    void readsTheMatrixValuesARunsOnExpressionChoosesBetween() {
        String workflow = """
            jobs:
              build:
                strategy:
                  matrix:
                    runner: [ubuntu-24.04, ubuntu-24.04-arm]
                    include:
                      - runner: ubuntu-22.04
                runs-on: ${{ matrix.runner }}
              chosen:
                runs-on: ${{ inputs.arm && 'ubuntu-24.04-arm' || 'ubuntu-26.04' }}
            """;
        assertEquals(
            List.of(
                WORKFLOW + ":5: ubuntu-24.04",
                WORKFLOW + ":5: ubuntu-24.04-arm",
                WORKFLOW + ":7: ubuntu-22.04",
                WORKFLOW + ":10: ubuntu-24.04-arm"
            ),
            workflowOffences("runner-matrix", workflow).stream().map(RunnerImageCheckTest::subject).toList()
        );
    }

    @Test
    void readsALabelWithoutRegardToCase() {
        String workflow = "jobs:\n  a:\n    runs-on: Ubuntu-Latest\n  b:\n    runs-on: UBUNTU-24.04-ARM\n";
        assertEquals(
            List.of(
                WORKFLOW + ":3: Ubuntu-Latest" + ALIASED + CURRENT,
                outdated(WORKFLOW + ":5", "UBUNTU-24.04-ARM", "24.04", CURRENT + "-ARM")
            ),
            workflowOffences("runner-case", workflow)
        );
    }

    @Test
    void passesTheCurrentAndALaterReleaseAndEveryLabelThatIsNoHostedUbuntuImage() {
        String workflow = """
            jobs:
              current:
                runs-on: ubuntu-26.04
              arm:
                runs-on: [ubuntu-26.04-arm]
              later:
                runs-on: ubuntu-28.04
              hosted:
                runs-on: [self-hosted, linux, x64]
              larger:
                runs-on: ubuntu-latest-8-core
              other:
                runs-on: windows-latest
              unread:
                runs-on: ${{ vars.RUNNER }}
              images:
                container: ubuntu:24.04
                services:
                  browser:
                    image: mcr.microsoft.com/playwright:v1.64.1-noble
                  base:
                    image: mcr.microsoft.com/devcontainers/base:ubuntu-24.04
                  path:
                    image: ghcr.io/example/ubuntu-22.04:1
                steps:
                  - uses: actions/upload-artifact@v4
                    with:
                      name: geometry-ubuntu-24.04
                      path: dist/ubuntu-22.04.tar.gz
                  - run: echo ubuntu-24.04@example
            """;
        assertEquals(List.of(), workflowOffences("runner-clean", workflow));
    }

    @Test
    void passesALabelInAComment() {
        String workflow = """
            jobs:
              # runs-on: ubuntu-latest
              build:
                runs-on: ubuntu-26.04 # was ubuntu-24.04
            """;
        assertEquals(List.of(), workflowOffences("runner-comment", workflow));
    }

    @Test
    void readsACompositeActionAndEveryAzurePipelinesDefinition() {
        RunnerImageCheck check = compositeAndAzureCheck();
        assertEquals(
            List.of(
                ".azure-pipelines/templates/build.yml:2: ubuntu-22.04",
                ".github/actions/setup/action.yml:4: ubuntu-22.04",
                "azure-pipelines.yml:2: ubuntu-latest",
                "ci/azure-pipelines.yaml:2: ubuntu-24.04"
            ),
            Verdicts.offences(check.findings(), HEADLINE).stream().map(RunnerImageCheckTest::subject).sorted().toList()
        );
        assertEquals(4, check.scanned(), "the composite action and the three Azure Pipelines files");
    }

    @Test
    void namesTheX64AndTheArmLabelOfTheRequiredRelease() {
        assertEquals(List.of(CURRENT, CURRENT + "-arm"), RunnerImageCheck.currentLabels());
    }

    @Test
    void passesARepositoryWithNoCiFile() {
        RunnerImageCheck check = new RunnerImageCheck(new GitFixture("runner-none").write("README.md", "x\n").root());
        assertTrue(Verdicts.clean(check.findings()), "nothing selects a runner");
        assertEquals(0, check.scanned(), "a check that read nothing says so");
    }

    private static RunnerImageCheck compositeAndAzureCheck() {
        Path root = new GitFixture("runner-files")
            .write(
                ".github/actions/setup/action.yml", "runs:\n  using: composite\n# ubuntu-latest\n  os: ubuntu-22.04\n"
            ).write("azure-pipelines.yml", "pool:\n  vmImage: ubuntu-latest\n")
            .write("ci/azure-pipelines.yaml", "pool:\n  vmImage: 'ubuntu-24.04'\n")
            .write(".azure-pipelines/templates/build.yml", "variables:\n  image: \"ubuntu-22.04\"\n")
            .write(".azure-pipelines/README.md", "ubuntu-latest\n")
            .write("docs/pipelines.yml", "vmImage: ubuntu-latest\n")
            .write(".github/workflows/nested/ci.yml", "runs-on: ubuntu-latest\n")
            .root();
        return new RunnerImageCheck(root);
    }

    private static String subject(String offence) {
        return offence.substring(0, offence.indexOf(" - "));
    }
}
