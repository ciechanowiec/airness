package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ComplianceReport;
import eu.ciechanowiec.airness.governance.ReportAdvisory;
import eu.ciechanowiec.airness.governance.ReportEvidence;
import eu.ciechanowiec.airness.governance.ReportImagePackage;
import eu.ciechanowiec.airness.governance.ReportInventory;
import eu.ciechanowiec.airness.governance.ReportLicensePolicy;
import eu.ciechanowiec.airness.governance.ReportMitigation;
import eu.ciechanowiec.airness.governance.ReportPackage;
import eu.ciechanowiec.airness.governance.ReportSource;
import eu.ciechanowiec.airness.governance.ReportUse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * A report with every kind of entry the printed report lays out: many Maven artifacts, npm packages the product ships
 * and ones only development reaches, long names and license expressions, a build tool, an open, a mitigated and an
 * awaiting finding, and the system packages of a shipped image.
 */
@UtilityClass
final class ReportSample {

    static final int PACKAGES = 35;
    static final String MODULE = "sample:application:jar:1.0";
    private static final String MIT = "MIT";
    private static final String APACHE = "Apache-2.0";
    private static final String DIRECT = "Direct";
    private static final String TRANSITIVE = "Transitive";
    private static final String IMAGE = "Dockerfile, on Alpine 3.24.4";
    private static final String DATE = "2026-10-13";

    static ComplianceReport report() {
        ReportInventory inventory = new ReportInventory(
            Stream.of(dependencies(), others()).flatMap(List::stream).toList(),
            Stream.concat(advisories().stream(), awaiting().stream()).toList(), new ReportLicensePolicy(
                Set.of(MIT, "MPL-2.0", APACHE, "GPL-2.0-with-classpath-exception"),
                Map.of("The Apache Software License, Version 2.0", APACHE)
            ), List.of(
                new ReportImagePackage(
                    IMAGE, "pkg:apk/alpine/libcrypto3@3.5.9-r0?distro=3.24.4", List.of(APACHE), true
                ),
                new ReportImagePackage(
                    IMAGE, "pkg:apk/alpine/busybox@1.37.0-r30?distro=3.24.4", List.of("GPL-2.0-only"), true
                )
            )
        );
        ReportEvidence evidence = new ReportEvidence(
            "{}", ReportJson.encode(Map.of("sbom", "{}", "csv", "inventory")),
            Map.of(
                "SBOM SHA-256", ReportJson.hash("{}"), "Modules", MODULE, MODULE + " scan date", "2026-10-13T11:58:00Z"
            )
        );
        return new ComplianceReport(
            "example-application", new ReportSource(
                "abcdef123456abcdef123456abcdef123456abcdef", "fingerprint", "2026-10-13T12:00:00Z", List.of()
            ), inventory, evidence
        );
    }

    private static List<ReportPackage> dependencies() {
        Stream<ReportPackage> many = IntStream.range(0, PACKAGES).mapToObj(
            index -> new ReportPackage(
                "sample:dependency-" + index + ":jar:1.0", List.of(MIT), List.of(
                    new ReportUse(MODULE, "compile", DIRECT)
                ),
                Map.of(
                    "META-INF/LICENSE", "Copyright 2026 Example Authors.\nPermission is granted to use this example."
                )
            )
        );
        ReportPackage copyleft = new ReportPackage(
            "sample:parser:jar:2.0", List.of("MPL-2.0"), List.of(new ReportUse(MODULE, "test", TRANSITIVE)), Map.of()
        );
        return Stream.concat(many, Stream.of(copyleft)).toList();
    }

    private static List<ReportPackage> others() {
        return List.of(
            new ReportPackage(
                "pkg:npm/%40example/runtime-framework-with-a-long-name@16.4.6", List.of(MIT),
                List.of(new ReportUse(MODULE, ReportUse.PRODUCTION, DIRECT)),
                Map.of("LICENSE", "MIT License\n\nCopyright (c) Example Authors.\n\nPermission is hereby granted.")
            ),
            new ReportPackage(
                "pkg:npm/lint-tool@2.0.0", List.of("Apache-2.0 AND MIT"),
                List.of(new ReportUse(MODULE, ReportUse.DEVELOPMENT, TRANSITIVE)), Map.of()
            ),
            new ReportPackage(
                "sample:enterprise-runtime-with-a-long-artifact-identifier:jar:3.0.0-RC1",
                List.of("The Apache Software License, Version 2.0", "GPL-2.0-with-classpath-exception"),
                List.of(new ReportUse(MODULE, "runtime", TRANSITIVE)), Map.of()
            ),
            new ReportPackage(
                "pkg:generic/node@26.13.0?checksum=sha256:0123456789abcdef", List.of(MIT),
                List.of(NpmReportInputs.tool(MODULE)), Map.of()
            )
        );
    }

    private static List<ReportAdvisory> advisories() {
        return List.of(
            new ReportAdvisory(
                "CVE-2026-1001", "HIGH", "pkg:maven/sample/dependency-1@1.0?type=jar", List.of(MODULE),
                "A crafted archive exhausts the heap. It needs untrusted input.", Optional.empty(), List.of()
            ),
            new ReportAdvisory(
                "CVE-2026-1002", "MEDIUM", "pkg:maven/sample/parser@2.0?type=jar", List.of(MODULE),
                "Deeply nested input overflows the stack.", Optional.of(
                    ReportMitigation.of(
                        "Added " + DATE + ". The parser reads only the project's own test fixtures. Mitigation: "
                            + "It never runs in the deployed application. The entry is removed once the parser "
                            + "releases a fix.", true
                    )
                ), List.of()
            )
        );
    }

    private static List<ReportAdvisory> awaiting() {
        return Stream.of("libcrypto3", "libssl3").map(
            name -> new ReportAdvisory(
                "ALPINE-CVE-2026-1003", "Unknown", "pkg:apk/alpine/" + name + "@3.5.9-r0?distro=3.24.4", List.of(
                    MODULE
                ),
                "A retransmitted frame can release packet storage twice.", Optional.of(
                    new ReportMitigation(
                        "Alpine has published a fixed version of openssl, but no newer release of the base ships it"
                            + " yet.", "The check fails once a newer release of the base ships the fixed version.",
                        DATE, ReportMitigation.Kind.AWAITING_FIX
                    )
                ), List.of("CVE-2026-1003")
            )
        ).toList();
    }
}
