#!/usr/bin/env sh

# Real application states prove the upstream motion, clipping, missing-page and completed-scan contracts.
run_browser_evidence_cases() {
    for mode in defects valid unexecuted; do
        prepare_browser_fixture "browser-evidence-${mode}"
        browser_generate create-browser-evidence-case "${browser_fixture}" "${mode}"
        commit_browser_fixture "${mode} browser evidence"
        if [ "${mode}" = unexecuted ]; then
            AIRNESS_FIXTURE_SCAN=no
            export AIRNESS_FIXTURE_SCAN
        fi
        run_maven "browser_evidence_${mode}" node "${browser_fixture}" -Pextended,delta \
            airness:node-gate@node-gate-build airness:node-gate@node-gate-e2e
        unset AIRNESS_FIXTURE_SCAN
        if [ "${mode}" = valid ]; then
            expect_exit browser_evidence_valid 'browser evidence: repaired pages and completed project scans pass' 0
        elif [ "${mode}" = unexecuted ]; then
            expect_exit browser_evidence_unexecuted 'browser evidence: an unexecuted project scan fails' 1
            expect_browser_line browser_evidence_unexecuted 'route-unexecuted'
        else
            expect_exit browser_evidence_defects 'browser evidence: real motion, clipping and error-page defects fail' 1
            for finding in '[clipped-text] /clipping' '[axe:landmark-main-is-top-level] not-found:' \
                'normal motion):' 'focus does not return' 'activation did not open the disclosure' \
                '[obscured] /pointer' 'holds focus but is not perceivable'; do
                expect_browser_line browser_evidence_defects "${finding}"
            done
        fi
    done

    for mode in defects valid; do
        prepare_browser_fixture "browser-locale-${mode}"
        browser_generate create-locale-not-found-case "${browser_fixture}" "${mode}"
        commit_browser_fixture "${mode} localized missing pages"
        run_maven "browser_locale_${mode}" node "${browser_fixture}" -Pextended,delta \
            airness:node-gate@node-gate-build airness:node-gate@node-gate-e2e
        if [ "${mode}" = valid ]; then
            expect_exit browser_locale_valid 'browser locale: redirected homes and valid error layouts pass' 0
        else
            expect_exit browser_locale_defects 'browser locale: nested landmarks fail in each missing-page layout' 1
            expect_browser_line browser_locale_defects 'landmark-main-is-top-level'
            expect_browser_line browser_locale_defects 'not-found:/en/__airness_missing_page__'
            expect_browser_line browser_locale_defects 'not-found:/fr/__airness_missing_page__'
        fi
    done
}
