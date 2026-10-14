#!/usr/bin/env sh

run_license_metadata_cases() {
    new_consumer license-metadata
    python3 - "${consumer_directory}/pom.xml" <<'PYTHON'
import sys
from pathlib import Path

pom = Path(sys.argv[1])
coordinates = (
    ("com.github.jtidy", "jtidy", "1.0.5"),
    ("org.codehaus.plexus", "plexus-i18n", "1.0-beta-10"),
    ("xalan", "serializer", "2.7.3"),
)
dependencies = "<dependencies>" + "".join(
    f"<dependency><groupId>{group}</groupId><artifactId>{artifact}</artifactId>"
    f"<version>{version}</version><scope>test</scope>"
    # These fixtures test three artifacts' metadata independently of their runtime transitives.
    "<exclusions><exclusion><groupId>*</groupId><artifactId>*</artifactId></exclusion></exclusions></dependency>"
    for group, artifact, version in coordinates
) + "</dependencies>"
pom.write_text(pom.read_text().replace("</project>", dependencies + "</project>"))
PYTHON
    run_maven license_verified maven "${consumer_directory}" \
        airness:license-metadata license:add-third-party
    expect_exit license_verified 'licenses: verified metadata and the HTMLTIDY alias pass the native check' 0
    license_metadata_file="${consumer_directory}/target/airness/licenses/THIRD-PARTY.properties"
    expect_file_match "${license_metadata_file}" 'licenses: Plexus is assigned its verified Apache license' \
        '^org.codehaus.plexus--plexus-i18n--1.0-beta-10=Apache-2.0$'
    expect_file_match "${license_metadata_file}" 'licenses: serializer is assigned its verified Apache license' \
        '^xalan--serializer--2.7.3=Apache-2.0$'
    perl -0pi -e 's{<version>2[.]7[.]3</version>}{<version>2.7.2</version>}' "${consumer_directory}/pom.xml"
    run_maven license_declared maven "${consumer_directory}" \
        airness:license-metadata license:add-third-party
    expect_exit license_declared 'licenses: an older release with its own license needs no correction' 0
    expect_file_no_match "${license_metadata_file}" 'licenses: a version change removes the stale correction' \
        'xalan--serializer'
    perl -0pi -e \
        's{<groupId>xalan</groupId><artifactId>serializer</artifactId><version>2.7.2</version>}{<groupId>org.codehaus.plexus</groupId><artifactId>plexus-classworlds</artifactId><version>1.2-alpha-7</version>}' \
        "${consumer_directory}/pom.xml"
    run_maven license_unknown maven "${consumer_directory}" airness:license-metadata license:add-third-party
    expect_exit license_unknown 'licenses: unknown licenses still fail the native check' 1
    expect_match license_unknown 'licenses: the unknown artifact is identified' 'plexus-classworlds'
    perl -0pi -e \
        's{<groupId>org.codehaus.plexus</groupId><artifactId>plexus-classworlds</artifactId><version>1.2-alpha-7</version>}{<groupId>org.openrewrite.recipe</groupId><artifactId>rewrite-apache</artifactId><version>2.30.0</version>}' \
        "${consumer_directory}/pom.xml"
    run_maven license_refused maven "${consumer_directory}" airness:license-metadata license:add-third-party
    expect_exit license_refused 'licenses: the correction mechanism does not admit source-available licenses' 1
    expect_match license_refused 'licenses: the refused license is identified' 'Moderne Source Available License'
}
