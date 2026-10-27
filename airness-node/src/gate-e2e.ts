export { BROWSER_INSTALL_REPAIR, judgeRun } from './browser-verdict.js'

// The end-to-end suite of a browser application: the harness's accessibility and layout sweep, its
// security-header spec, and the member's own end-to-end specs, run by Playwright under the shipped
// configuration against the application the configuration starts.
import { browserServerProblems } from './browser-servers.js'
import { runBrowserSweep } from './browser-suite.js'
import { failed, type GateContext, type GateResult, notApplicable } from './exec.js'
import { isBrowserApp, isNextApp } from './facets.js'
import { asStringRecord, readKey } from './json-shapes.js'
import { currentBuild, type StampedBuild } from './next-build.js'
import type { Member } from './project.js'

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

const staleBuild = (member: Member): string | undefined => {
  if (!isNextApp(member)) {
    return undefined
  }
  const recorded: StampedBuild | { readonly problem: string } = currentBuild(member)
  return 'problem' in recorded ? recorded.problem : undefined
}

const EMPTY_CONTEXT: GateContext = { evidenceDirectory: undefined }

/**
 * Run the end-to-end suite of a browser application.
 * @param member the member.
 * @returns not applicable for a member that is not a browser application; otherwise the suite's verdict.
 */
export const endToEnd = async (member: Member, context: GateContext = EMPTY_CONTEXT): Promise<GateResult> => {
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
  const occupied: readonly string[] = await browserServerProblems([
    member.settings.serverUrl,
    ...member.settings.auxiliaryServers.map((server): string => server.url),
  ])
  if (occupied.length > 0) {
    return failed('the browser endpoint is not free', occupied)
  }
  return await runBrowserSweep(member, context)
}
