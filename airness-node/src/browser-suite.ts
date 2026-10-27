import { createHash } from 'node:crypto'
// Runtime orchestration is exercised by the browser contracts; the gate's preconditions and the
// scope, clock, ownership and result policies are measured by their unit suites.
import { appendFileSync, copyFileSync } from 'node:fs'
import path from 'node:path'
import {
  BROWSER_ENDPOINT_ENVIRONMENT_VARIABLE,
  BROWSER_TUNNEL_ENVIRONMENT_VARIABLE,
  SWEEP_WORKERS_ENVIRONMENT_VARIABLE,
} from './browser-run.js'
import { VERIFICATION_ENVIRONMENT_VARIABLE } from './browser-server-policy.js'
import { judgeRun } from './browser-verdict.js'
import { isScopeFailure, sweepScopeFor, withScopeFile } from './change-scope.js'
import { SWEEP_SCOPE_ENVIRONMENT_VARIABLE, type SweepScope, scopeHeadline } from './change-scope-policy.js'
import { clockedEnvironment } from './clock-environment.js'
import { type GateContext, type GateResult, runStreaming } from './exec.js'
import { isStartFailure, type PinnedBrowsers, startPinnedBrowsers } from './pinned-browsers.js'
import { harnessDirectory, type Member, resolveTool } from './project.js'
import { isProgressLine } from './sweep-progress.js'

/** Run the complete or delta sweep and retain its scope evidence beside the gate result. */
export const runBrowserSweep = async (member: Member, context: GateContext): Promise<GateResult> => {
  if (process.env['AIRNESS_VERIFICATION_MODE'] === 'full') {
    return await runSuite(member)
  }
  const scope: SweepScope | GateResult = sweepScopeFor(member)
  if (isScopeFailure(scope)) {
    return scope
  }
  return await withScopeFile(scope, async (file: string): Promise<GateResult> => {
    const outcome: GateResult = await runSuite(member, file)
    const name: string = `scope-${createHash('sha256').update(member.path).digest('hex')}.json`
    if (context.evidenceDirectory !== undefined) {
      copyFileSync(file, path.join(context.evidenceDirectory, name))
    }
    return {
      ...outcome,
      summary: `${outcome.summary}; delta sweep: ${scopeHeadline(scope)}`,
      ...(context.evidenceDirectory !== undefined && { evidence: [name] }),
    }
  })
}

const runSuite = async (member: Member, scopeFile?: string): Promise<GateResult> => {
  const browsers: PinnedBrowsers | GateResult = await startPinnedBrowsers(member)
  if (isStartFailure(browsers)) {
    return browsers
  }
  const config: string = path.join(harnessDirectory(), 'dist', 'playwright.js')
  try {
    return judgeRun(
      await runStreaming(
        process.execPath,
        [resolveTool('@playwright/test', 'playwright'), 'test', '--config', config],
        {
          cwd: member.root,
          onLine: (line: string): void => {
            const log: string | undefined = process.env['AIRNESS_GATE_LOG']
            if (log !== undefined) {
              appendFileSync(log, `${line}\n`)
            }
            if (isProgressLine(line)) {
              console.info(line)
            }
          },
          env: {
            NODE_ENV: 'production',
            [VERIFICATION_ENVIRONMENT_VARIABLE]: '1',
            NEXT_TELEMETRY_DISABLED: '1',
            ...clockedEnvironment(member, '--no-deprecation'),
            [BROWSER_ENDPOINT_ENVIRONMENT_VARIABLE]: browsers.endpoint,
            [SWEEP_WORKERS_ENVIRONMENT_VARIABLE]: String(browsers.workers),
            [SWEEP_SCOPE_ENVIRONMENT_VARIABLE]: scopeFile,
            [BROWSER_TUNNEL_ENVIRONMENT_VARIABLE]: browsers.isTunnelled ? '1' : undefined,
          },
        },
      ),
    )
  } finally {
    browsers.stop()
  }
}
