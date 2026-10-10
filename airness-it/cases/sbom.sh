#!/usr/bin/env sh

run_sbom_cases() {
    prepare_sbom_consumer
    run_maven sbom_install maven "${sbom_consumer}" clean install -DskipTests
    expect_exit sbom_install 'sbom: packaging and installation generate an attached inventory when tests are skipped' 0
    expect_match sbom_install 'sbom: the inherited generator runs once' 'cyclonedx:2[.]9[.]3:makeBom [(]airness-sbom[)]'
    expect_no_match sbom_install 'sbom: JSON schema validation recognizes the schema annotation keywords' \
        'Unknown keyword'
    if python3 "${repository}/airness-it/sbom-check.py" "${sbom_consumer}/target/bom.json" sbom-consumer \
        "${sbom_consumer}"; then
        pass 'sbom: JSON inventory preserves dependency scopes, relationships and privacy'
    else
        fail 'sbom: JSON inventory preserves dependency scopes, relationships and privacy'
    fi
    sbom_installed="${local_repository}/com/example/sbom-consumer/9.4.2/sbom-consumer-9.4.2-cyclonedx.json"
    if cmp "${sbom_consumer}/target/bom.json" "${sbom_installed}"; then
        pass 'sbom: install publishes the classified JSON artifact'
    else
        fail 'sbom: install publishes the classified JSON artifact'
    fi
    cp "${sbom_consumer}/target/bom.json" "${scratch}/first-bom.json"
    run_maven sbom_deploy maven "${sbom_consumer}" deploy -DskipTests \
        "-DaltDeploymentRepository=fixture::file://${scratch}/sbom-repository"
    expect_exit sbom_deploy 'sbom: deployment to a local repository includes the SBOM' 0
    sbom_deployed="${scratch}/sbom-repository/com/example/sbom-consumer/9.4.2/sbom-consumer-9.4.2-cyclonedx.json"
    if cmp "${scratch}/first-bom.json" "${sbom_deployed}"; then
        pass 'sbom: repeated generation is reproducible'
    else
        fail 'sbom: repeated generation is reproducible'
    fi
    run_sbom_bypasses
    run_sbom_publication
    run_sbom_reactor
}

prepare_sbom_consumer() {
    new_consumer sbom-consumer
    sbom_consumer="${consumer_directory}"
    perl -0pi -e 's{<artifactId>consumer</artifactId>}{<artifactId>sbom-consumer</artifactId>}' \
        "${sbom_consumer}/pom.xml"
    sbom_dependencies="$(cat <<'XML'
    <dependencies>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-text</artifactId>
            <version>1.14.0</version>
            <scope>compile</scope>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>commons-codec</groupId>
            <artifactId>commons-codec</artifactId>
            <version>1.19.0</version>
            <scope>runtime</scope>
        </dependency>
    </dependencies>
XML
)"
    SBOM_DEPENDENCIES="${sbom_dependencies}" perl -0pi -e \
        's{</project>}{$ENV{SBOM_DEPENDENCIES}."\n</project>"}e' "${sbom_consumer}/pom.xml"
}

run_sbom_bypasses() {
    run_maven sbom_offline_compile maven "${sbom_consumer}" test -o -DskipTests
    expect_exit sbom_offline_compile 'sbom: earlier phases still work offline with cached dependencies' 0
    run_maven sbom_offline maven "${sbom_consumer}" package -o -DskipTests -Dairness.enforce=false
    expect_exit sbom_offline 'sbom: offline packaging cannot silently omit the SBOM' 1
    expect_match sbom_offline 'sbom: offline refusal names the repair' 'SBOM generation requires online Maven mode'
    run_maven sbom_skip maven "${sbom_consumer}" package -DskipTests -Dcyclonedx.skip=true
    expect_exit sbom_skip 'sbom: the native skip flag cannot bypass packaging' 1
    expect_match sbom_skip 'sbom: the skip refusal names the generator' 'Airness packaging requires an SBOM'
    run_maven sbom_override maven "${sbom_consumer}" package -DskipTests -Dcyclonedx.skipAttach=true \
        -DincludeCompileScope=false -DincludeProvidedScope=false -DincludeRuntimeScope=false -DoutputName=hidden \
        -DincludeTestScope=true -DexcludeTypes=jar -DincludeBomSerialNumber=true
    expect_exit sbom_override 'sbom: literal harness configuration resists native property overrides' 0
    expect_match sbom_override 'sbom: attachment remains enabled' 'attaching as sbom-consumer-9.4.2-cyclonedx.json'
    if python3 "${repository}/airness-it/sbom-check.py" "${sbom_consumer}/target/bom.json" sbom-consumer \
        "${sbom_consumer}"; then
        pass 'sbom: native scope properties cannot narrow the inventory'
    else
        fail 'sbom: native scope properties cannot narrow the inventory'
    fi
    rm "${sbom_consumer}/target/bom.json"
    run_maven sbom_missing maven "${sbom_consumer}" enforcer:enforce@airness-sbom-output -DskipTests
    expect_exit sbom_missing 'sbom: a missing inventory fails the output check' 1
    expect_match sbom_missing 'sbom: a missing inventory has an actionable message' 'Missing SBOM'
}

run_sbom_publication() {
    new_consumer sbom-pom
    sbom_pom="${consumer_directory}"
    perl -0pi -e 's{<artifactId>consumer</artifactId>}{<artifactId>sbom-pom</artifactId><packaging>pom</packaging>}' \
        "${sbom_pom}/pom.xml"
    run_maven sbom_pom_build maven "${sbom_pom}" package -DskipTests airness:publication-content
    expect_exit sbom_pom_build 'sbom: POM publication needs its inventory without requiring JARs' 0
    printf '{"local":"%s"}\n' "${sbom_pom}" > "${sbom_pom}/target/bom.json"
    run_maven sbom_pom_path maven "${sbom_pom}" airness:publication-content -DskipTests
    expect_exit sbom_pom_path 'sbom: publication rejects local paths in the inventory even when tests are skipped' 1
    expect_match sbom_pom_path 'sbom: publication identifies the leaking file' 'bom[.]json'
    rm "${sbom_pom}/target/bom.json"
    run_maven sbom_pom_missing maven "${sbom_pom}" airness:publication-content -DskipTests
    expect_exit sbom_pom_missing 'sbom: POM publication rejects a missing inventory' 1
}

run_sbom_reactor() {
    new_consumer sbom-reactor
    sbom_reactor="${consumer_directory}"
    perl -0pi -e \
        's{<artifactId>consumer</artifactId>}{<artifactId>sbom-reactor</artifactId><packaging>pom</packaging>}; s{</project>}{<modules><module>plain</module><module>boot</module></modules></project>}' \
        "${sbom_reactor}/pom.xml"
    for sbom_module in plain boot; do
        new_consumer "sbom-${sbom_module}"
        mv "${consumer_directory}" "${sbom_reactor}/${sbom_module}"
        perl -0pi -e "s{<artifactId>consumer</artifactId>}{<artifactId>sbom-${sbom_module}</artifactId>}" \
            "${sbom_reactor}/${sbom_module}/pom.xml"
    done
    perl -0pi -e 's{<artifactId>airness-parent</artifactId>}{<artifactId>airness-parent-spring-boot</artifactId>}' \
        "${sbom_reactor}/boot/pom.xml"
    run_maven sbom_reactor_build maven "${sbom_reactor}" clean package -DskipTests
    expect_exit sbom_reactor_build 'sbom: a reactor with plain and Spring parents generates each module inventory' 0
    for sbom_module in plain boot; do
        expect_file_match "${sbom_reactor}/${sbom_module}/target/bom.json" \
            "sbom: ${sbom_module} receives its own module identity" "pkg:maven/com.example/sbom-${sbom_module}@9.4.2"
    done
    expect_file_match "${sbom_reactor}/target/bom.json" 'sbom: the reactor root receives its own inventory' \
        'pkg:maven/com.example/sbom-reactor@9.4.2'
}
