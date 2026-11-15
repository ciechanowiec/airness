package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A compose file lifts a container's isolation when a service gains a privilege, shares a namespace of the host or
 * mounts the Docker socket, exposes a port when it publishes beyond loopback, and writes a credential when it gives
 * one a value.
 */
class ComposeHardeningTest {

    private static final String PRIVILEGES = """
        services:
          app:
            image: app:1.0.0
            privileged: true
            cap_add:
              - NET_ADMIN
            security_opt:
              - seccomp=unconfined
              - no-new-privileges:true
            network_mode: host
            pid: "host"
            volumes:
              - /var/run/docker.sock:/var/run/docker.sock
              - data:/data
          worker:
            image: worker:1.0.0
            privileged: "true"
            cap_add: [SYS_ADMIN, "SYS_PTRACE"]
            security_opt: ["apparmor:unconfined", "label=disable"]
            ipc: host
            userns_mode: host
            uts: host
            cgroup: host
            volumes:
              - type: bind
                source: /var/run/docker.sock
                target: /var/run/docker.sock
          quiet:
            image: quiet:1.0.0
            privileged: false
            network_mode: bridge
            pid: "service:app"
            security_opt:
              - seccomp:unconfined
        """;

    private static final String PORTS = """
        services:
          app:
            ports:
              - "8080"
              - "8081:80"
              - "${APP_PORT}:80"
              - "0.0.0.0:8082:80"
              - "127.0.0.1:${APP_PORT}:80"
              - "127.0.0.1::80"
              - "[::1]::80"
              - target: 80
                published: "8083"
              - target: 81
                host_ip: 127.0.0.1
              - {target: 82, published: "8084"}
              - {target: 83, host_ip: "::1"}
            expose:
              - "9000"
          db:
            ports: ["5432:5432", "127.0.0.1::5432"]
        """;

    private static final String ENVIRONMENTS = """
        services:
          db:
            environment:
              POSTGRES_PASSWORD: postgres
              POSTGRES_USER: postgres
              APP_SECRET: ${APP_SECRET:-development}
              API_TOKEN: ${API_TOKEN}
              SIGNING_KEY: "${SIGNING_KEY:?set the signing key}"
              CACHE_PASSWORD:
              EMPTY_SECRET: ""
              HOST_TOKEN: $HOST_TOKEN
          app:
            environment:
              - DB_PASSWD=hunter2
              - ACCESS_KEY=${ACCESS_KEY}
              - PASSED_TOKEN
              - NEXT_PUBLIC_MAPS_KEY=public-by-design
          worker:
            environment: ["WORKER_TOKEN=fixed", "WORKER_NAME=worker"]
          cron:
            environment: {CRON_SECRET: fixed, CRON_PASSWORD: "${CRON_PASSWORD}"}
        """;

    private static final String OUTSIDE = """
        x-defaults:
          privileged: true
          ports:
            - "8080:80"
          environment:
            DB_PASSWORD: postgres
        volumes:
          data:
            driver_opts:
              device: /var/run/docker.sock
        """;

    @Test
    void namesEveryPrivilegeAServiceGainsInEverySpelling() {
        assertEquals(
            List.of(
                "4: app runs privileged",
                "6: app adds the capability NET_ADMIN",
                "8: app sets security_opt seccomp=unconfined",
                "10: app shares the host's namespace through network_mode: host",
                "11: app shares the host's namespace through pid: host",
                "13: app mounts the Docker socket",
                "17: worker runs privileged",
                "18: worker adds the capability SYS_ADMIN",
                "18: worker adds the capability SYS_PTRACE",
                "19: worker sets security_opt apparmor:unconfined",
                "19: worker sets security_opt label=disable",
                "20: worker shares the host's namespace through ipc: host",
                "21: worker shares the host's namespace through userns_mode: host",
                "22: worker shares the host's namespace through uts: host",
                "23: worker shares the host's namespace through cgroup: host",
                "26: worker mounts the Docker socket",
                "34: quiet sets security_opt seccomp:unconfined"
            ),
            described(ComposeHardening.isolationLifts(PRIVILEGES))
        );
    }

    @Test
    void namesEveryPortPublishedBeyondLoopbackInEverySyntax() {
        assertEquals(
            List.of(
                "4: app publishes 8080 beyond loopback",
                "5: app publishes 8081:80 beyond loopback",
                "6: app publishes ${APP_PORT}:80 beyond loopback",
                "7: app publishes 0.0.0.0:8082:80 beyond loopback",
                "11: app publishes target 80 beyond loopback",
                "15: app publishes target 82 beyond loopback",
                "20: db publishes 5432:5432 beyond loopback"
            ),
            described(ComposeHardening.exposedPorts(PORTS))
        );
    }

    @Test
    void namesEveryCredentialTheFileGivesAValueInEverySpelling() {
        assertEquals(
            List.of(
                "4: db gives POSTGRES_PASSWORD a value",
                "6: db gives APP_SECRET a value",
                "14: app gives DB_PASSWD a value",
                "19: worker gives WORKER_TOKEN a value",
                "21: cron gives CRON_SECRET a value"
            ),
            described(ComposeHardening.valuedCredentials(ENVIRONMENTS))
        );
    }

    @Test
    void readsNothingOutsideTheServices() {
        assertEquals(
            List.of(),
            List.of(
                ComposeHardening.isolationLifts(OUTSIDE), ComposeHardening.exposedPorts(OUTSIDE),
                ComposeHardening.valuedCredentials(OUTSIDE)
            ).stream()
                .flatMap(List::stream).toList()
        );
    }

    // Each finding as its line and text, so an expectation reads as the report does.
    private static List<String> described(List<Located<String>> findings) {
        return findings.stream().map(finding -> finding.line() + ": " + finding.value()).toList();
    }
}
