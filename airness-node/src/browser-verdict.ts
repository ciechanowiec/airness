import { playwrightImageOf } from './browser-run.js'
import { fromRun, type RunResult } from './exec.js'
import type { GateResult } from './gate-result.js'
import { resolvedVersion } from './project.js'

/** The repair for a run that found no browser to drive. */
const BROWSER_INSTALL_REPAIR: string = `Playwright found no browser executable; run docker pull ${playwrightImageOf(resolvedVersion('@playwright/test'))} before verification`

// What Playwright prints when the browser it was asked to launch is not where it looks for it.
const MISSING_BROWSER: RegExp = /Executable doesn't exist|Please run the following command to download new browsers/u

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
