#!/usr/bin/env sh

run_template_cases() {
    new_consumer template-findings
    template_consumer="$consumer_directory"
    mkdir -p "$template_consumer/src/main/resources/templates"
    cat > "$template_consumer/src/main/resources/templates/fragments.html" <<'HTML'
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<body>
<div th:fragment="panel(one, two, three, four, five, six)">
    <span th:text="${one}">One</span>
</div>
</body>
</html>
HTML
    cat > "$template_consumer/src/main/resources/templates/page.html" <<'HTML'
<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<body>
<div th:if="${ready}" th:replace="~{fragments :: panel('1', '2', '3', '4', '5', '6')}"></div>
<div th:replace="~{fragments :: missing('one')}"></div>
<img alt="asset" th:src="@{${@assets.path()}}">
<span th:utext="${content}">Content</span>
<span th:text="__${expression}__">Expression</span>
</body>
</html>
HTML

    run_maven template_report_only templates "$template_consumer" \
        airness:template-parse \
        airness:template-fragments \
        airness:template-replacements \
        airness:template-links \
        airness:template-calls \
        airness:template-output \
        -Dairness.enforce=false
    expect_exit template_report_only 'templates: compatible packaged goals share one report-only execution' 0
    expect_count template_report_only 'templates: every requested packaged goal executed exactly once' \
        'airness:[^ ]+:template-(parse|fragments|replacements|links|calls|output) \(default-cli\)' 6
    expect_match template_report_only 'templates: fragment arity is visible at its fixture' \
        'fragments[.]html.*takes 6 arguments'
    expect_match template_report_only 'templates: discarded conditions stay visible at their fixture' \
        'page[.]html.*discards th:if'
    expect_match template_report_only 'templates: forbidden link reach stays visible at its fixture' \
        'page[.]html.*th:src reaches for a bean'
    expect_match template_report_only 'templates: missing fragment calls stay visible at their fixture' \
        'page[.]html.*fragment.*missing'
    expect_match template_report_only 'templates: unescaped output stays visible at its fixture' \
        'page[.]html.*th:utext writes its value as markup'
    expect_match template_report_only 'templates: expression preprocessing stays visible at its fixture' \
        'page[.]html.*an expression is preprocessed'

    run_maven template_enforcement templates "$template_consumer" airness:template-fragments
    expect_exit template_enforcement 'templates: a representative installed-goal finding fails enforcement' 1
    expect_match template_enforcement 'templates: enforcement names the fragment fixture and offence' \
        'fragments[.]html.*takes 6 arguments'
}
