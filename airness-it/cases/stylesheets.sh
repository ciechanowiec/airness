#!/usr/bin/env sh

run_stylesheet_cases() {
    new_consumer stylesheet-selectors
    stylesheet_consumer="${consumer_directory}"
    mkdir -p "${stylesheet_consumer}/src/main/resources/static/css"
    mkdir -p "${stylesheet_consumer}/src/main/resources/static/vendor"
    cat > "${stylesheet_consumer}/src/main/resources/static/css/theme.css" <<'CSS'
a:not(.button):not(.rail-item) :not(.crumb):not(.tile-name) {
    color: #2453ff;
}

.table :is(th, td) {
    padding: 0.5rem;
}
CSS
    cat > "${stylesheet_consumer}/src/main/resources/static/vendor/published.css" <<'CSS'
a:not(.one):not(.two) :not(.three) {
    color: #000000;
}
CSS

    run_maven stylesheet_report_only stylesheets "${stylesheet_consumer}" \
        airness:stylesheet-selectors -Dairness.enforce=false
    expect_exit stylesheet_report_only 'stylesheets: the packaged goal runs report-only' 0
    expect_match stylesheet_report_only 'stylesheets: a selector broken across lines is visible at its fixture' \
        'theme[.]css:1.*a space joins two refusals'
    expect_no_match stylesheet_report_only 'stylesheets: a group written inside something is left alone' \
        'theme[.]css:5'
    expect_no_match stylesheet_report_only 'stylesheets: a stylesheet somebody else published is not read' \
        'published[.]css'

    run_maven stylesheet_enforcement stylesheets "${stylesheet_consumer}" airness:stylesheet-selectors
    expect_exit stylesheet_enforcement 'stylesheets: a selector meaning something else fails enforcement' 1
    expect_match stylesheet_enforcement 'stylesheets: enforcement names the selector and its repair' \
        'theme[.]css.*name the parts in one refusal'
}
