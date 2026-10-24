#!/usr/bin/env sh

# The node infrastructure, proven with a fake gate: a few lines of JavaScript in a pnpm workspace member of
# the fixture, which writes a result in the node-gate contract and names the Node it ran on. The installed
# parent must download and verify the pinned Node and pnpm, install each declared project's locked
# dependencies, build a frontend with node-run on that Node, fail the build on a failing gate, and fail a
# module that carries a package.json nobody declared, a declared folder without one, or a project that picks a
# pnpm of its own. A project may declare the Airness plugin only to bind node-run, so the fake gate runs as a
# command-line goal after verify, which adds a gate and replaces none. The node gates the parent binds run in
# verify as well, so each frontend installs the @airness/node this reactor packed and conforms to them. The
# variants (path resolution, result parsing, workspace membership, the exec, antrun and frontend-maven-plugin
# refusals) are unit-tested in airness-maven-plugin and airness-governance.
run_node_project_cases() {
    node_fixture="${repository}/airness-it/fixtures/node-project"

    new_consumer node-shop
    node_shop="${consumer_directory}"
    mkdir -p "${node_shop}/src/main"
    cp -R "${node_fixture}" "${node_shop}/src/main/frontend"
    install_airness_node "${node_shop}/src/main/frontend"
    write_node_consumer "${node_shop}" 'src/main/frontend' '
    <build>
        <plugins>
            <plugin>
                <groupId>eu.ciechanowiec</groupId>
                <artifactId>airness-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>frontend-build</id>
                        <phase>generate-resources</phase>
                        <goals>
                            <goal>node-run</goal>
                        </goals>
                        <configuration>
                            <project>src/main/frontend</project>
                            <script>build</script>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>'

    run_maven node_shop node "${node_shop}" airness:assets-sync clean verify airness:node-gate \
        -Dairness.node.gate=fake '-Dairness.node.command=airness-fake-gate gate fake'
    expect_exit node_shop 'node: a Java module with an in-module frontend passes' 0
    expect_match node_shop 'node: the pinned Node and pnpm are installed and verified' \
        'Node 26[.]11[.]1 and pnpm 12[.]14[.]0, verified by SHA-256'
    expect_match node_shop 'node: the frontend dependencies are installed from its lockfile, running no script' \
        'Running pnpm install --frozen-lockfile --ignore-scripts --ignore-pnpmfile in .*/src/main/frontend'
    expect_match node_shop 'node: only the build scripts allowBuilds approves run, after the install' \
        'Running pnpm rebuild --ignore-pnpmfile --config[.]strict-dep-builds=false --config[.]dangerously-allow-all-builds=false in'
    node_store="${node_shop}/src/main/frontend/node_modules/.pnpm"
    expect_marker 'node: a build script allowBuilds approves runs' "${node_store}" 'fixture-approved' yes
    expect_marker 'node: a build script allowBuilds refuses does not run' "${node_store}" 'fixture-denied' no
    expect_marker 'node: an approved build of a workspace member alone runs too' "${node_store}" 'fixture-member' yes
    if [ -e "${node_shop}/src/main/frontend/root-postinstall-ran" ] \
        || [ -e "${node_shop}/src/main/frontend/root-prepare-ran" ]; then
        fail 'node: the project lifecycle scripts never run' 'a root postinstall or prepare marker exists'
    else
        pass 'node: the project lifecycle scripts never run'
    fi
    expect_match node_shop 'node: the gate runs in the declared project on the harness Node' \
        'Node gate fake in src/main/frontend passed: ran on Node v26[.]11[.]1'
    expect_file_match "${node_shop}/target/classes/static/index.html" \
        'node: node-run builds the frontend into the artifact on the harness Node' 'built on Node v26[.]11[.]1'

    run_maven node_shop_skip node "${node_shop}" clean package -DskipTests airness:node-gate \
        -Dairness.node.gate=fake '-Dairness.node.command=airness-fake-gate gate fake'
    expect_exit node_shop_skip 'node: Skip mode still builds the frontend of a module that runs node-run' 0
    unzip -p "${node_shop}/target/consumer-9.4.2.jar" static/index.html > "${scratch}/node-shop-index.html" \
        2>/dev/null || true
    expect_file_match "${scratch}/node-shop-index.html" \
        'node: Skip mode packs the frontend node-run built into the jar' 'built on Node v26[.]11[.]1'
    expect_no_match node_shop_skip 'node: Skip mode runs no node gate' 'Node gate fake'

    new_consumer node-portal
    node_portal="${consumer_directory}"
    mkdir -p "${node_portal}/src/main/webapp" "${node_portal}/ui"
    cp -R "${node_fixture}" "${node_portal}/src/main/webapp/admin"
    cp -R "${node_fixture}" "${node_portal}/ui/storefront"
    install_airness_node "${node_portal}/src/main/webapp/admin"
    install_airness_node "${node_portal}/ui/storefront"
    write_node_consumer "${node_portal}" 'src/main/webapp/admin,ui/storefront' ''

    run_maven node_portal node "${node_portal}" airness:assets-sync clean verify airness:node-gate \
        -Dairness.node.gate=fake '-Dairness.node.command=airness-fake-gate gate fake'
    expect_exit node_portal 'node: two frontends in custom folders pass' 0
    for node_project in src/main/webapp/admin ui/storefront; do
        expect_match node_portal "node: the gate runs in ${node_project}" \
            "Node gate fake in ${node_project} passed: ran on Node v26[.]11[.]1"
        expect_file_match "${node_portal}/target/airness/node/projects/${node_project}/fake.json" \
            "node: the result for ${node_project} lies under its own key" '"gate":"fake"'
    done

    # A failing result fails the build: the fake gate fails where a planted-finding.txt lies.
    printf 'planted\n' > "${node_portal}/ui/storefront/planted-finding.txt"
    run_maven node_failing node "${node_portal}" verify airness:node-gate \
        -Dairness.node.gate=fake '-Dairness.node.command=airness-fake-gate gate fake'
    expect_exit node_failing 'node: a failing gate fails the build' 1
    expect_match node_failing 'node: the failure names the gate finding' 'planted-finding[.]txt is planted'

    # The case that matters most: a Java module gains a package.json in a folder nobody declared. One
    # validate run batches it with a declared folder that holds no package.json and a project that names a
    # pnpm of its own, since node-projects reports all three in one verdict and none can hide another.
    new_consumer node-undeclared
    node_undeclared="${consumer_directory}"
    mkdir -p "${node_undeclared}/ui" "${node_undeclared}/web"
    printf '{\n  "private": true\n}\n' > "${node_undeclared}/ui/package.json"
    printf '{\n  "private": true,\n  "packageManager": "pnpm@11.0.0"\n}\n' > "${node_undeclared}/web/package.json"
    write_node_consumer "${node_undeclared}" 'web,missing' ''
    run_maven node_undeclared node "${node_undeclared}" airness:assets-sync validate
    expect_exit node_undeclared 'node: an undeclared package.json fails the module' 1
    expect_match node_undeclared 'node: the failure names the frontend and the property to fix' \
        'ui/package[.]json is a node project that no gate checks; add it to airness[.]node[.]projects'
    expect_match node_undeclared 'node: a declared folder without a package.json fails' \
        'missing has no package[.]json; fix airness[.]node[.]projects'
    expect_match node_undeclared 'node: a project may not choose a pnpm of its own' \
        'web/package[.]json: packageManager lets pnpm choose a pnpm or Node of its own'
}

# Rewrites a cloned consumer's pom to declare its node projects, with any build plugins the case needs.
write_node_consumer() {
    cat > "$1/pom.xml" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>eu.ciechanowiec</groupId>
        <artifactId>airness-parent-java</artifactId>
        <version>1.0.8-SNAPSHOT</version>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>consumer</artifactId>
    <version>9.4.2</version>
    <properties>
        <airness.node.projects>$2</airness.node.projects>
        <airness.package.root>com.example</airness.package.root>
    </properties>$3
</project>
POM
}

# Asserts whether the postinstall marker of a fixture package exists in the virtual store.
expect_marker() {
    marker_found="$(find "$2" -path "*$3*" -name postinstall-ran 2>/dev/null | head -n 1)"
    if { [ "$4" = yes ] && [ -n "${marker_found}" ]; } || { [ "$4" = no ] && [ -z "${marker_found}" ]; }; then
        pass "$1"
    else
        fail "$1" "postinstall marker of $3 present: ${marker_found:-none}, expected $4"
    fi
}
