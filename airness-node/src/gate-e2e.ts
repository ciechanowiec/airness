// The end-to-end suite of a browser application: the harness's accessibility and layout sweep, its
// security-header spec, and the member's own end-to-end specs, run by Playwright under the shipped
// configuration against the application the configuration starts.
import path from 'node:path'
import { VERIFICATION_ENVIRONMENT_VARIABLE } from './browser-server-policy.js'
import { browserServerProblems } from './browser-servers.js'
import { failed, fromRun, type GateResult, notApplicable, type RunResult, runNode } from './exec.js'
import { isBrowserApp, isNextApp } from './facets.js'
import { asStringRecord, readKey } from './json-shapes.js'
import { currentBuild, type StampedBuild } from './next-build.js'
import { harnessDirectory, type Member, resolveTool } from './project.js'

/** The repair for a run that found no browser to drive. */
export const BROWSER_INSTALL_REPAIR: string =
  'Playwright found no browser executable; run `mvn airness:report-browser-install`, which installs the ' +
  'Chromium of the pinned Playwright version into ~/.cache/airness/report-browser/<version>'

// What Playwright prints when the browser it was asked to launch is not where it looks for it.
const MISSING_BROWSER: RegExp = /Executable doesn't exist|Please run the following command to download new browsers/u

const SERVE_EXAMPLE: string = '"airness": { "serve": "<script>" }'

/**
 * Why the member cannot be served, if it cannot. A Next.js application is started by the harness itself;
 * any other browser application names the package.json script that serves it, and that script must exist.
 * @param member the browser application.
 * @returns the finding, or undefined when the member can be served.
 */
export const serveProblem = (member: Member): string | undefined => {
  const serve: string | undefined = member.settings.serve
  if (serve === undefined) {
    return isNextApp(member)
      ? undefined
      : `${member.path} is a browser application that is not a Next.js application, so the harness cannot ` +
          `start it: declare ${SERVE_EXAMPLE} in package.json, naming the script that serves it`
  }
  return Object.hasOwn(asStringRecord(readKey(member.packageJson, 'scripts')), serve)
    ? undefined
    : `airness.serve names the script "${serve}", which package.json does not declare; add it under ` +
        '"scripts" or name the script that serves the application'
}

/**
 * The verdict of a Playwright run, naming the browser install when the run found no browser to drive.
 * @param result the run's outcome.
 * @returns the gate result.
 */
export const judgeRun = (result: RunResult): GateResult => {
  const verdict: GateResult = fromRun(
    result,
    'the end-to-end suite passes',
    'the end-to-end suite failed, or the application could not be served',
  )
  return !verdict.ok && MISSING_BROWSER.test(result.output)
    ? { ...verdict, findings: [BROWSER_INSTALL_REPAIR, ...verdict.findings] }
    : verdict
}

const staleBuild = (member: Member): string | undefined => {
  if (!isNextApp(member)) {
    return undefined
  }
  const recorded: StampedBuild | { readonly problem: string } = currentBuild(member)
  return 'problem' in recorded ? recorded.problem : undefined
}

/**
 * Run the end-to-end suite of a browser application.
 * @param member the member.
 * @returns not applicable for a member that is not a browser application; otherwise the suite's verdict.
 */
export const endToEnd = async (member: Member): Promise<GateResult> => {
  if (!isBrowserApp(member)) {
    return notApplicable(`${member.path} is not a browser application`)
  }
  const problem: string | undefined = serveProblem(member)
  if (problem !== undefined) {
    return failed('the browser application cannot be served', [problem])
  }
  // A Next.js application is served from its production build, which must be the one the build gate made of
  // this tree: a stale build would be judged in its place.
  const stale: string | undefined = staleBuild(member)
  if (stale !== undefined) {
    return failed('no current production build to serve', [stale])
  }
  // Refused before Playwright starts, because a verification run never reuses a server: a listener already
  // on the port would make Playwright's own readiness check fail with a message naming no cause.
  const occupied: readonly string[] = await browserServerProblems([member.settings.serverUrl])
  if (occupied.length > 0) {
    return failed('the browser endpoint is not free', occupied)
  }
  const config: string = path.join(harnessDirectory(), 'dist', 'playwright.js')
  return judgeRun(
    runNode(resolveTool('@playwright/test', 'playwright'), ['test', '--config', config], {
      cwd: member.root,
      env: { NODE_ENV: 'production', [VERIFICATION_ENVIRONMENT_VARIABLE]: '1', NEXT_TELEMETRY_DISABLED: '1' },
    }),
  )
}
