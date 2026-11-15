package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every tracked Dockerfile is held to its base image's packages, each upgrading command named at its file and
 * line with the repair in the headline.
 */
class DockerfileUpgradeCheckTest {

    private static final String HEADLINE = "upgrade the system packages of their base image";

    @Test
    void namesTheFileAndLineOfEveryUpgradingCommand() {
        Path root = new GitFixture("dockerfile-upgrades-found")
            .write("Dockerfile", "FROM alpine:3.24\nRUN apk upgrade --no-cache\n")
            .write("deploy/worker.Dockerfile", "FROM debian:13\nRUN apt-get update\nRUN apt-get -y upgrade\n")
            .write("web/Dockerfile", "FROM node:26-alpine\nRUN apk add --no-cache curl\n")
            .root();
        DockerfileUpgradeCheck check = new DockerfileUpgradeCheck(root);
        assertAll(
            () -> assertEquals(3, check.scanned()),
            () -> assertEquals(
                List.of(
                    "Dockerfile:2 runs `apk upgrade --no-cache`",
                    Path.of("deploy", "worker.Dockerfile") + ":3 runs `apt-get -y upgrade`"
                ),
                Verdicts.offences(check.findings(), HEADLINE)
            ),
            () -> assertTrue(
                check.findings().getFirst().headline().contains("move FROM to a newer release of the base image"),
                "the headline names the repair, so an agent does not reach for another way to patch the image"
            )
        );
    }

    @Test
    void passesATreeWhoseDockerfilesTakeTheirPackagesFromTheirBase() {
        Path root = new GitFixture("dockerfile-upgrades-clean")
            .write("Dockerfile", "FROM debian:13\nRUN apt-get update && apt-get install -y curl\n")
            .root();
        assertTrue(Verdicts.clean(new DockerfileUpgradeCheck(root).findings()));
    }

    @Test
    void judgesAFileGivenByItsText() {
        assertEquals(
            List.of("starter/Dockerfile:2 runs `dnf -y update`"),
            DockerfileUpgradeCheck.offences("starter/Dockerfile", "FROM fedora:43\nRUN dnf -y update\n")
        );
    }
}
