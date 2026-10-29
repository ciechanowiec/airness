#!/usr/bin/env sh

# Managed files follow capabilities rather than the parent chain. One monorepo proves both wiring facts:
# its root inherits only the generic parent yet carries the Java and node blocks, and the Java module's
# embedded frontend receives the node block although that module never names a node parent. The selection
# rules themselves are unit-tested in airness-governance; these runs prove the installed goals read the
# whole repository's models, from the root and from inside one member.
run_managed_file_cases() {
    ensure_consumer_template
    platform="${scratch}/managed-platform"
    rm -rf "${platform}"
    mkdir -p "${platform}/shop/src/main/frontend" "${platform}/web"
    cp -R "${consumer_template}/.mvn" "${platform}/.mvn"
    cat > "${platform}/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>eu.ciechanowiec</groupId>
        <artifactId>airness-parent</artifactId>
        <version>1.0.9-SNAPSHOT</version>
        <relativePath/>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>platform</artifactId>
    <version>9.4.2</version>
    <packaging>pom</packaging>
    <modules>
        <module>shop</module>
        <module>web</module>
    </modules>
    <properties>
        <airness.package.root>com.example</airness.package.root>
    </properties>
</project>
POM
    cat > "${platform}/shop/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>eu.ciechanowiec</groupId>
        <artifactId>airness-parent-java</artifactId>
        <version>1.0.9-SNAPSHOT</version>
        <relativePath/>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>shop</artifactId>
    <version>9.4.2</version>
    <properties>
        <airness.node.projects>src/main/frontend</airness.node.projects>
        <airness.package.root>com.example</airness.package.root>
    </properties>
</project>
POM
    cat > "${platform}/web/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>eu.ciechanowiec</groupId>
        <artifactId>airness-parent</artifactId>
        <version>1.0.9-SNAPSHOT</version>
        <relativePath/>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>web</artifactId>
    <version>9.4.2</version>
    <packaging>pom</packaging>
    <properties>
        <airness.node.projects>.</airness.node.projects>
        <airness.package.root>com.example</airness.package.root>
    </properties>
</project>
POM
    printf '{\n  "private": true\n}\n' > "${platform}/shop/src/main/frontend/package.json"
    printf '{\n  "private": true\n}\n' > "${platform}/web/package.json"
    printf '# Platform instructions\n\nRun the Maven verification before committing a change.\n' \
        > "${platform}/AGENTS.md"
    git -C "${platform}" init --quiet
    git -C "${platform}" config user.name Fixture
    git -C "${platform}" config user.email fixture@example.invalid
    git -C "${platform}" add --all
    git -C "${platform}" commit --quiet --message 'test(it): create a monorepo fixture' \
        --message 'The fixture proves that managed files follow the capabilities of every module.'

    run_maven managed_root repository "${platform}" airness:assets-sync validate
    expect_exit managed_root 'managed files: a synced monorepo root passes its asset check' 0
    for managed_capability in generic java node; do
        expect_file_match "${platform}/.gitignore" \
            "managed files: the generic root carries the ${managed_capability} .gitignore block" \
            "^# BEGIN AIRNESS MANAGED: ${managed_capability}$"
        expect_file_match "${platform}/AGENTS.md" \
            "managed files: the root AGENTS.md carries the ${managed_capability} block" \
            "^<!-- BEGIN AIRNESS MANAGED: ${managed_capability} -->$"
    done
    expect_file_match "${platform}/shop/src/main/frontend/AGENTS.md" \
        'managed files: the frontend embedded in a Java module receives the node block' \
        '^<!-- BEGIN AIRNESS MANAGED: node -->$'
    expect_file_no_match "${platform}/shop/src/main/frontend/AGENTS.md" \
        'managed files: the embedded frontend carries no root block' 'MANAGED: (generic|java)'
    expect_file_match "${platform}/web/AGENTS.md" \
        'managed files: a node module receives the node block' '^<!-- BEGIN AIRNESS MANAGED: node -->$'
    if [ ! -e "${platform}/shop/AGENTS.md" ]; then
        pass 'managed files: a Java module relies on the root Java block'
    else
        fail 'managed files: a Java module relies on the root Java block' 'shop/AGENTS.md was written'
    fi

    # Inside one member the reactor holds neither the Java module nor the frontend. The planted drift in
    # the frontend must still be reported, and the root's Java block must not be called unexpected.
    printf '# Frontend instructions\n' > "${platform}/shop/src/main/frontend/AGENTS.md"
    run_maven managed_member repository "${platform}/web" validate -Dairness.enforce=false
    expect_exit managed_member 'managed files: a member-only run reports without stopping' 0
    expect_match managed_member 'managed files: a member-only run still reads the embedded frontend' \
        'shop/src/main/frontend/AGENTS[.]md [(]run mvn airness:assets-sync to restore its managed blocks'
    expect_count managed_member 'managed files: a member-only run accepts every block the root needs' \
        'to restore its managed blocks' 1
}
