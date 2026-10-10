#!/usr/bin/env sh

run_repository_routing_cases() {
    new_consumer repository-routing
    routing_consumer="${consumer_directory}"
    run_maven routing_skipped maven "${routing_consumer}" validate -DskipTests -Dairness.enforce=false
    expect_exit routing_skipped 'routing: startup policy accepts an ordinary consumer with tests skipped' 0
    expect_match routing_skipped 'routing: the inherited preflight runs when tests are skipped' \
        'repository-policy [(]airness-preflight[)]'
    printf '<settings/>\n' > "${scratch}/alternate-settings.xml"
    run_maven routing_alternate maven "${routing_consumer}" validate -DskipTests -Dairness.enforce=false \
        -gs "${scratch}/alternate-settings.xml"
    expect_exit routing_alternate 'routing: alternate global settings fail even in bypass modes' 1
    expect_match routing_alternate 'routing: an alternate configuration names the repair' \
        'remove the -gs/--global-settings override'
    run_routing_user_settings
    run_routing_managed_files
    new_consumer repository-traffic
    if python3 "${repository}/airness-it/repository-routing.py" "${consumer_directory}"; then
        pass 'routing: dependency repositories receive no requests during CycloneDX resolution'
    else
        fail 'routing: dependency repositories receive no requests during CycloneDX resolution'
    fi
}

run_routing_user_settings() {
    cat > "${scratch}/user-settings.xml" <<'XML'
<settings><servers><server><id>publication</id><username>publisher</username></server></servers></settings>
XML
    run_maven routing_credentials maven "${routing_consumer}" validate -DskipTests \
        -s "${scratch}/user-settings.xml"
    expect_exit routing_credentials 'routing: user settings may retain deployment credentials' 0
    cat > "${scratch}/user-settings.xml" <<'XML'
<settings><mirrors><mirror><id>other</id><mirrorOf>central</mirrorOf>
<url>https://repo.maven.apache.org/maven2/</url></mirror></mirrors></settings>
XML
    run_maven routing_user_override maven "${routing_consumer}" validate -DskipTests \
        -s "${scratch}/user-settings.xml"
    expect_exit routing_user_override 'routing: a user mirror cannot override the managed wildcard' 1
    expect_match routing_user_override 'routing: effective settings disagreement identifies mirror overrides' \
        'remove mirror overrides from user or alternate settings'
}

run_routing_managed_files() {
    printf '\n' >> "${routing_consumer}/.mvn/settings.xml"
    run_maven routing_drift maven "${routing_consumer}" validate -DskipTests -Dairness.enforce=false \
        -Dairness.assets.unmanaged=.mvn/settings.xml
    expect_exit routing_drift 'routing: startup files cannot be exempted or changed' 1
    expect_match routing_drift 'routing: changed files name asset synchronization' 'must match Airness'
    run_maven routing_repair maven "${routing_consumer}" airness:assets-sync
    expect_exit routing_repair 'routing: the explicit repair restores the startup settings' 0
    mkdir "${routing_consumer}/child"
    cp "${routing_consumer}/pom.xml" "${routing_consumer}/child/pom.xml"
    run_maven routing_child maven "${routing_consumer}/child" validate -DskipTests
    expect_exit routing_child 'routing: startup settings resolve from a child working directory' 0
    run_maven routing_outside maven "${scratch}" -f "${routing_consumer}/child/pom.xml" validate -DskipTests
    expect_exit routing_outside 'routing: startup settings resolve through an external -f invocation' 0
    rm "${routing_consumer}/.mvn/settings.xml"
    run_maven routing_restore maven "${routing_consumer}" \
        -gs "\${maven.conf}/settings.xml" airness:assets-sync
    expect_exit routing_restore 'routing: missing settings can be restored through the explicit bootstrap command' 0
    run_maven routing_restored maven "${routing_consumer}" validate -DskipTests
    expect_exit routing_restored 'routing: a new invocation reads the repaired startup settings' 0
}
