#!/usr/bin/env sh

# Maven hands a child its parent's description, address, licenses, developers and source control, and the chain
# above a consumer ends in the Airness aggregator, so a consumer states each of them itself. These cases plant one
# departure each in the template consumer, whose own metadata passes, and read the refusal validate prints.
run_project_metadata_cases() {
    run_root_metadata_case metadata-missing 'project metadata: a root pom that inherits its licenses fails' \
        's{\n    <licenses>.*?</licenses>\n}{}s' '<licenses> is not declared'
    run_root_metadata_case metadata-unlicense 'project metadata: the Unlicense is refused for the project' \
        's{LicenseRef-Proprietary}{Unlicense}' 'Unlicense places the code in the public domain'
    run_root_metadata_case metadata-address 'project metadata: an address that is not https is refused' \
        's{<url>https://git[.]example[.]invalid/consumer</url>}{<url>http://git.example.invalid/consumer</url>}' \
        'url http://git[.]example[.]invalid/consumer is not an https address'
    run_root_metadata_case metadata-inception 'project metadata: an inception year in the future is refused' \
        's{<inceptionYear>2026</inceptionYear>}{<inceptionYear>2999</inceptionYear>}' \
        'inceptionYear 2999 is after this year'
    run_root_metadata_case metadata-copied 'project metadata: a description copied from Airness is refused' \
        's{<description>[^<]*</description>}{<description>The reusable Java build and repository governance harness</description>}' \
        "<description> repeats Airness's own"
    run_root_metadata_case metadata-example-group 'project metadata: a group under an example domain is refused' \
        's{<groupId>org[.]sample</groupId>}{<groupId>com.example.shop</groupId>}' \
        'groupId com[.]example[.]shop names an example domain'
    run_root_metadata_case metadata-version 'project metadata: a version that is not major.minor.patch is refused' \
        's{<version>9[.]4[.]2</version>}{<version>9.4</version>}' 'version 9[.]4 is not major[.]minor[.]patch'
    run_inherited_metadata_case
    run_pom_portability_case
    run_own_group_license_case
}

# Plants one change in a copy of the template consumer and expects validate to refuse it with the given message.
run_root_metadata_case() {
    new_consumer "$1"
    perl -0pi -e "$3" "${consumer_directory}/pom.xml"
    metadata_execution="$(printf '%s' "$1" | tr '-' '_')"
    run_maven "${metadata_execution}" analysis "${consumer_directory}" validate
    expect_exit "${metadata_execution}" "$2" 1
    expect_match "${metadata_execution}" "$2, naming the value" "$4"
}

# A module that inherits an Airness parent directly never passes through the root, so its metadata would come
# from Airness unless it declares its own, and below the root it is named after its directory.
run_inherited_metadata_case() {
    new_consumer metadata-reactor
    metadata_reactor="${consumer_directory}"
    new_consumer metadata-module
    rm -rf "${consumer_directory}/.git"
    mv "${consumer_directory}" "${metadata_reactor}/api"
    rm -rf "${metadata_reactor}/src"
    perl -0pi -e 's{(    <version>9[.]4[.]2</version>\n)}{$1    <packaging>pom</packaging>\n};
        s{(\n    <scm>)}{\n    <modules>\n        <module>api</module>\n    </modules>\n$1}' \
        "${metadata_reactor}/pom.xml"
    perl -0pi -e 's{<artifactId>consumer</artifactId>}{<artifactId>api-module</artifactId>};
        s{\n    <name>.*?</scm>\n}{}s' \
        "${metadata_reactor}/api/pom.xml"
    run_maven metadata_reactor analysis "${metadata_reactor}" validate -Dairness.enforce=false
    expect_match metadata_reactor 'project metadata: a module on an Airness parent inherits no Airness license' \
        '<licenses> comes from eu[.]ciechanowiec:airness'
    expect_match metadata_reactor 'project metadata: a module is named after its directory' \
        'artifactId api-module differs from its directory api'
}

# Declarations that build differently by machine or reach outside the repository, each in the template consumer.
run_pom_portability_case() {
    new_consumer pom-portability
    pom_portability="${consumer_directory}"
    portability_blocks="$(cat <<'XML'

    <build>
        <sourceDirectory>source</sourceDirectory>
    </build>

    <reporting>
        <excludeDefaults>true</excludeDefaults>
    </reporting>

    <profiles>
        <profile>
            <id>default</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
        </profile>
        <profile>
            <id>far</id>
            <modules>
                <module>../outside</module>
            </modules>
        </profile>
    </profiles>
XML
)"
    PORTABILITY_BLOCKS="${portability_blocks}" perl -0pi -e \
        's{\n</project>}{$ENV{PORTABILITY_BLOCKS}\n</project>}' "${pom_portability}/pom.xml"
    run_maven pom_portability analysis "${pom_portability}" validate
    expect_exit pom_portability 'pom portability: machine-dependent declarations fail validation' 1
    expect_match pom_portability 'pom portability: a profile active by default is named' \
        'profile default is active by default'
    expect_match pom_portability 'pom portability: site reporting is named' '<reporting>'
    expect_match pom_portability 'pom portability: a moved source directory is named' \
        '<build> moves sourceDirectory source'
    expect_match pom_portability 'pom portability: a module outside the repository is named' \
        'module [.][.]/outside lies outside the repository'
}

# A proprietary project's own artifacts carry its own license, which the dependency allowlist does not admit and
# has no business judging: a library its owner publishes under the same group passes the license check.
run_own_group_license_case() {
    new_consumer own-group-license
    own_group_license="${consumer_directory}"
    own_library="${scratch}/own-group-library"
    mkdir -p "${own_library}"
    cat > "${own_library}/pom.xml" <<'XML'
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.sample</groupId>
    <artifactId>own-group-library</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <licenses>
        <license>
            <name>LicenseRef-Proprietary</name>
        </license>
    </licenses>
</project>
XML
    run_maven own_group_install analysis "${own_library}" install:install-file \
        -Dfile="${own_library}/pom.xml" -DpomFile="${own_library}/pom.xml" -Dpackaging=pom
    expect_exit own_group_install 'license: the library of the project owner installs' 0
    own_dependency="$(cat <<'XML'

    <dependencies>
        <dependency>
            <groupId>org.sample</groupId>
            <artifactId>own-group-library</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>compile</scope>
        </dependency>
    </dependencies>
XML
)"
    OWN_DEPENDENCY="${own_dependency}" perl -0pi -e 's{(\n    </properties>\n)}{$1$ENV{OWN_DEPENDENCY}\n}' \
        "${own_group_license}/pom.xml"
    run_maven own_group_license analysis "${own_group_license}" \
        license:add-third-party@airness-check-dependency-licenses
    expect_exit own_group_license 'license: a proprietary library of the project owner passes the allowlist' 0
    expect_no_match own_group_license 'license: the library of the project owner is not judged as third-party' \
        'LicenseRef-Proprietary'
}
