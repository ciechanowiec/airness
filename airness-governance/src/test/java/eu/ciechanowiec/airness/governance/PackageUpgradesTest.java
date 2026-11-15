package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every package manager a base image ships is recognised in each spelling it upgrades with, while refreshing an
 * index and installing a new package pass.
 */
class PackageUpgradesTest {

    @Test
    void recognisesEveryUpgradeSpelling() {
        assertAll(
            Stream.of(
                "apk upgrade",
                "apk -U upgrade",
                "apk --no-cache upgrade --available",
                "/sbin/apk upgrade",
                "apk add --upgrade zlib",
                "apk add -u zlib",
                "apk add --no-cache -Uu zlib",
                "apt-get upgrade -y",
                "apt-get -y dist-upgrade",
                "apt full-upgrade",
                "apt-get -o Dpkg::Options::=--force-confnew upgrade",
                "apt-get install -y --only-upgrade libssl3",
                "aptitude safe-upgrade",
                "dnf update",
                "dnf -y upgrade",
                "yum update -y",
                "microdnf update",
                "tdnf distro-sync",
                "zypper up",
                "zypper --non-interactive dup",
                "sudo apt-get upgrade"
            ).map(command -> () -> assertTrue(PackageUpgrades.upgrades(command), command))
        );
    }

    @Test
    void passesWhatInstallsNoNewerVersionOfAPackageTheImageHolds() {
        assertAll(
            Stream.of(
                "apk update",
                "apk add --no-cache curl",
                "apk add -U curl",
                "apt-get update",
                "apt-get install -y --no-install-recommends curl",
                "apt-get install -y unattended-upgrades",
                "dnf install -y curl",
                "zypper install curl",
                "pip install --upgrade pip",
                "npm update",
                "echo upgrade",
                ""
            ).map(command -> () -> assertFalse(PackageUpgrades.upgrades(command), command))
        );
    }

    @Test
    void readsEachUpgradingCommandOfARunLineAtTheLineItsInstructionStarts() {
        String dockerfile = """
            FROM alpine:3.24
            # RUN apk upgrade
            RUN apk update && apk upgrade --no-cache
            RUN apt-get update \\
                && apt-get -y dist-upgrade \\
                ; echo done
            CMD ["apk", "upgrade"]
            """;
        assertEquals(
            List.of(new Located<>(3, "apk upgrade --no-cache"), new Located<>(4, "apt-get -y dist-upgrade")),
            DockerfileReader.packageUpgrades(dockerfile)
        );
    }
}
