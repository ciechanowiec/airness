export { BROWSER_INSTALL_REPAIR, judgeRun } from './browser-verdict.js'

// The end-to-end suite of a browser application: the harness's accessibility and layout sweep, its
// security-header spec, and the member's own end-to-end specs, run by Playwright under the shipped
// configuration against the application the configuration starts.
import { fixedPorts, freePorts, portsFor, type ServedOrigins, servedOrigins } from './browser-origin.js'
import { browserServerProblems } from './browser-servers.js'
import { runBrowserSweep } from './browser-suite.js'
import { failed, type GateContext, type GateResult, notApplicable } from './exec.js'
import { isBrowserApp, isNextApp } from './facets.js'
import { asStringRecord, readKey } from './json-shapes.js'
import { currentBuild, type StampedBuild } from './next-build.js'
import type { Member } from './project.js'
import { withPretestAsync } from './test-invocation.js'

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

/**
 * The addresses a run serves on: the declared ones, each on a port the operating system chose for this run, so
 * neither a developer's server on the declared port nor another verification on the same machine collides. An
 * auxiliary server declared with a fixed port keeps it, and no chosen port is one of those.
 * @param member the browser application.
 * @returns the application's address and the auxiliary servers', in declaration order.
 */
export const assignOrigins = async (member: Member): Promise<ServedOrigins> => {
  const fixed: readonly number[] = fixedPorts(member.settings)
  const chosen: readonly number[] = await freePorts(member.settings.auxiliaryServers.length + 1 - fixed.length, fixed)
  return servedOrigins(member.settings, portsFor(member.settings, chosen))
}

/**
 * Why a run cannot serve on the addresses it chose, if it cannot. Checked before Playwright starts, because a
 * verification run never reuses a server: something that took a chosen port in the moment since it was
 * released would make Playwright's own readiness check fail with a message naming no cause.
 * @param origins the addresses the run chose.
 * @returns the failure, or undefined when every address is free.
 */
export const occupiedOrigins = async (origins: ServedOrigins): Promise<GateResult | undefined> => {
  const occupied: readonly string[] = await browserServerProblems([origins.serverUrl, ...origins.auxiliaryUrls])
  return occupied.length > 0 ? failed('the browser endpoint is not free', occupied) : undefined
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
  const origins: ServedOrigins = await assignOrigins(member)
  const occupied: GateResult | undefined = await occupiedOrigins(origins)
  if (occupied !== undefined) {
    return occupied
  }
  return await withPretestAsync(
    member,
    async (): Promise<GateResult> => await runBrowserSweep(member, context, origins),
  )
}
