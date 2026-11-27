package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.ciechanowiec.airness.governance.Findings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NodeInstallFilesTest {

    private static final String NPMRC = NodeInstallFiles.NPMRC;
    private static final String WORKSPACE = PnpmWorkspace.FILE;
    private static final String CERTIFICATE = " turns the registry's certificate check off";
    private static final String PLAIN = " is reached over plain http";

    @TempDir
    private Path module;

    @ParameterizedTest
    @ValueSource(strings = {"package-lock.json", "npm-shrinkwrap.json", "yarn.lock", "bun.lock", "bun.lockb"})
    void refusesEveryLockfilePnpmNeverReads(String lockfile) {
        assertEquals(
            List.of("web/" + lockfile + " is a lockfile pnpm never reads; remove it"),
            this.offences("web/" + lockfile, "")
        );
    }

    @Test
    void acceptsPnpmsOwnLockfile() {
        assertEquals(List.of(), this.offences("pnpm-lock.yaml", "lockfileVersion: '9.0'"));
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = {"strict-ssl=false|false", "strict-ssl = false|false", "strict-ssl=\"false\"|false",
            "strict-ssl='FALSE'|FALSE", "strict-ssl=false ; trusted proxy|false", "strict-ssl=false#note|false",
            "strict-ssl=0|0", "strict-ssl=no|no"}
    )
    void refusesAnNpmrcThatTurnsTheCertificateCheckOffInEverySpelling(String line, String value) {
        assertEquals(List.of(NPMRC + ": strict-ssl " + value + CERTIFICATE), this.offences(NPMRC, line + "\n"));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "registry=http://registry.example.invalid/", "@scope:registry=http://registry.example.invalid/",
            "registry=HTTP://registry.example.invalid/"
        }
    )
    void refusesARegistryOverPlainHttp(String line) {
        String key = line.substring(0, line.indexOf('='));
        assertEquals(
            List.of(NPMRC + ": " + key + " " + line.substring(line.indexOf('=') + 1) + PLAIN),
            this.offences(NPMRC, line + "\n")
        );
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "registry=https://registry.npmjs.org/", "registry=http://localhost:4873/", "registry=http://127.0.0.1:4873",
            "@airness:registry=http://[::1]:4873/", "strict-ssl=true", "# strict-ssl=false", "; registry=http://x/",
            "always-auth", "proxy=http://proxy.example.invalid:3128/", "strict-ssl=TRUE", "strict-ssl=true # checked",
            "registry=http://localhost:4873 ; the harness registry"
        }
    )
    void acceptsVerifiedHttpsALoopbackRegistryCommentsAndOtherSettings(String line) {
        assertEquals(List.of(), this.offences(NPMRC, line + "\n"));
    }

    @Test
    void refusesTheSameSettingsInTheWorkspaceFile() {
        assertEquals(
            List.of(
                WORKSPACE + ": strictSsl false" + CERTIFICATE,
                WORKSPACE + ": registry http://registry.example.invalid/" + PLAIN
            ),
            this.offences(WORKSPACE, "strictSsl: false\nregistry: http://registry.example.invalid/\n")
        );
    }

    @Test
    void readsNoOtherFile() {
        assertEquals(List.of(), this.offences("notes.txt", "strict-ssl=false\n"));
    }

    @SneakyThrows
    private List<String> offences(String name, String content) {
        Path file = this.module.resolve(name);
        Files.createDirectories(file.resolveSibling(""));
        Files.writeString(file, content);
        return new NodeInstallFiles(this.module, List.of(file)).findings().stream()
            .map(Findings::offences)
            .flatMap(List::stream)
            .toList();
    }
}
