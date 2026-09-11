#!/usr/bin/env sh

run_scanner_cases() {
    new_consumer scanner-consumer
    scanner_consumer="${consumer_directory}"
    printf '#!/bin/sh\nprintf "%%s\\n" ready\n' > "${scanner_consumer}/run.sh"
    run_maven shell_clean scanners "${scanner_consumer}" airness:shellcheck
    expect_exit shell_clean 'scanners: a supported shell script passes the installed goal' 0
    expect_match shell_clean 'scanners: the shell report names its selected input count' 'ShellCheck: 1 script'

    printf "#!/bin/sh\nvalue=\"hello world\"\necho \$value\n" > "${scanner_consumer}/run.sh"
    run_maven shell_failure scanners "${scanner_consumer}" airness:shellcheck
    expect_exit shell_failure 'scanners: an unsafe expansion fails enforcement' 1
    expect_match shell_failure 'scanners: the actual ShellCheck rule is reported' 'SC2086'

    printf '# shell%s source=/dev/null\n. run.sh\n' check > "${scanner_consumer}/hint.sh"
    run_maven shell_hint scanners "${scanner_consumer}" airness:shellcheck -Dairness.enforce=false
    expect_exit shell_hint 'scanners: source redirection fails even in report-only mode' 1
    expect_match shell_hint 'scanners: source redirection names the actual-import requirement' 'actual repository import'
    rm "${scanner_consumer}/hint.sh"
    printf '#!/bin/sh\nprintf "%%s\\n" ready\n' > "${scanner_consumer}/run.sh"

    printf 'FROM alpine:3.22\nUSER 65532\nWORKDIR /application\n' > "${scanner_consumer}/Dockerfile"
    mkdir -p "${scanner_consumer}/src/main/resources"
    printf 'type: recipe\n---\ntype: another-recipe\n' > "${scanner_consumer}/src/main/resources/settings.yaml"
    run_maven checkov_clean scanners "${scanner_consumer}" airness:checkov
    expect_exit checkov_clean 'scanners: a CLI container needs no health endpoint' 0
    expect_no_match checkov_clean 'scanners: application YAML is not parsed as CloudFormation' 'Invalid infrastructure'
    run_scanner_frameworks

    printf 'FROM alpine:3.22\nUSER root\nWORKDIR /application\n' > "${scanner_consumer}/Dockerfile"
    run_maven checkov_failure scanners "${scanner_consumer}" airness:checkov
    expect_exit checkov_failure 'scanners: the installed Checkov policy rejects a root runtime' 1
    expect_match checkov_failure 'scanners: the Checkov rule is actionable' 'CKV_DOCKER_8'
    printf 'soft-fail: true\n' > "${scanner_consumer}/.checkov.yaml"
    run_maven checkov_override scanners "${scanner_consumer}" airness:checkov -Dairness.enforce=false
    expect_exit checkov_override 'scanners: a native policy override cannot create a pass' 1
    expect_match checkov_override 'scanners: the override refusal explains ownership' 'configuration belongs to Airness'
    rm "${scanner_consumer}/.checkov.yaml" "${scanner_consumer}/Dockerfile"

    run_scanner_lifecycle

    new_consumer scanner-spring-parent
    perl -0pi -e 's{<artifactId>airness-parent</artifactId>}{<artifactId>airness-parent-spring-boot</artifactId>}' \
        "${consumer_directory}/pom.xml"
    printf '#!/bin/sh\nprintf "%%s\\n" ready\n' > "${consumer_directory}/run.sh"
    run_maven scanners_spring scanners "${consumer_directory}" airness:shellcheck airness:checkov
    expect_exit scanners_spring 'scanners: the Spring parent supplies the same mandatory scanner policy' 0
}

run_scanner_lifecycle() {
    new_consumer scanner-lifecycle
    scanner_consumer="${consumer_directory}"
    printf "#!/bin/sh\nvalue=\"two words\"\necho \$value\n" > "${scanner_consumer}/run.sh"
    run_maven shell_bound scanners "${scanner_consumer}" clean verify -Pextended
    expect_exit shell_bound 'scanners: inherited Extended execution rejects shell defects' 1
    expect_match shell_bound 'scanners: the shell goal is bound in the parent' \
        'shellcheck \(airness-shell-and-iac\)'
    expect_no_match shell_bound 'scanners: shell defects stop before Qodana' \
        'qodana \(airness-qodana\)'
    printf '#!/bin/sh\nprintf "%%s\\n" ready\n' > "${scanner_consumer}/run.sh"
    printf 'FROM alpine:3.22\nUSER root\n' > "${scanner_consumer}/Dockerfile"
    run_maven checkov_bound scanners "${scanner_consumer}" clean verify -Pextended
    expect_exit checkov_bound 'scanners: inherited Extended execution rejects IaC defects' 1
    expect_match checkov_bound 'scanners: the IaC goal is bound in the parent' \
        'checkov \(airness-shell-and-iac\)'
    expect_no_match checkov_bound 'scanners: IaC defects stop before Qodana' \
        'qodana \(airness-qodana\)'
}

run_scanner_frameworks() {
    mkdir -p "${scanner_consumer}/infra/kubernetes" "${scanner_consumer}/infra/chart/templates" \
        "${scanner_consumer}/infra/overlay" "${scanner_consumer}/infra/terraform"
    cat > "${scanner_consumer}/infra/kubernetes/configmap.yaml" <<'YAML'
apiVersion: v1
kind: ConfigMap
metadata:
  name: settings
data:
  image: ordinary-configuration-data
YAML
    cp "${scanner_consumer}/infra/kubernetes/configmap.yaml" "${scanner_consumer}/infra/chart/templates/configmap.yaml"
    cp "${scanner_consumer}/infra/kubernetes/configmap.yaml" "${scanner_consumer}/infra/overlay/configmap.yaml"
    printf 'apiVersion: v2\nname: scanner\nversion: 0.1.0\n' > "${scanner_consumer}/infra/chart/Chart.yaml"
    printf 'resources:\n  - configmap.yaml\n' > "${scanner_consumer}/infra/overlay/kustomization.yaml"
    printf 'resource "random_id" "value" {\n  byte_length = 8\n}\n' > "${scanner_consumer}/infra/terraform/main.tf"
    run_maven checkov_frameworks scanners "${scanner_consumer}" airness:checkov
    expect_exit checkov_frameworks 'scanners: real Terraform Kubernetes Helm and Kustomize inputs pass' 0
    for scanner_framework in terraform kubernetes helm kustomize; do
        expect_match checkov_frameworks "scanners: ${scanner_framework} is actually invoked" "Checkov ${scanner_framework}:"
    done
    printf 'resource "aws_s3_bucket" "unsafe" {\n  bucket = "airness-scanner-fixture"\n}\n' \
        > "${scanner_consumer}/infra/terraform/main.tf"
    run_maven checkov_terraform scanners "${scanner_consumer}" airness:checkov
    expect_exit checkov_terraform 'scanners: real Terraform security findings block verification' 1
    expect_match checkov_terraform 'scanners: Terraform findings retain their Checkov identifiers' 'CKV.*AWS_'
    printf 'resource "aws_s3_bucket" "broken" {\n' > "${scanner_consumer}/infra/terraform/main.tf"
    run_maven checkov_parse scanners "${scanner_consumer}" airness:checkov -Dairness.enforce=false
    expect_exit checkov_parse 'scanners: an IaC parse failure is never report-only success' 1
    rm -rf "${scanner_consumer}/infra"
}
