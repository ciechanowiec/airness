// Every collection and global in the BUILT configuration decides its access.
//
// The static rules read the access blocks a project wrote; this gate imports the configuration Payload actually
// boots, so the collections the framework builds for the project (the folder tree, the job queue, a plugin's
// own) are judged too. Each of those arrives with Payload's default, which admits every signed-in user, and the
// anonymous sweep cannot see that because a signed-in user is not anonymous.
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { readJsonFile } from './json-shapes.js'
import { asFindings, failed, type GateResult, notApplicable, passed, withOutput } from './outcome.js'
import {
  findInheritedAccess,
  findUnconstrainedDraftReads,
  type InheritedAccessReport,
  parseInheritedAccessReport,
  payloadConfigPathOf,
} from './payload-defaults.js'
import { analysisEnvironment, PAYLOAD_PACKAGE, type PayloadMember } from './payload-member.js'
import {
  findPayloadSecurityViolations,
  type PayloadSecurityReport,
  parsePayloadSecurityReport,
} from './payload-security.js'
import { type RunResult, runNode } from './process-run.js'

// Where Payload keeps the function it fills an undeclared operation with. Named by path because the package's
// exports map does not expose it; the layer pins `payload` exactly, so a layout change arrives with a ploaness
// release, and an absent file fails closed below.
const DEFAULT_ACCESS_MODULE: readonly string[] = ['dist', 'auth', 'defaultAccess.js']
// Building a configuration opens no socket, but a plugin might, and a hung import must end as a verdict.
const PROBE_TIMEOUT_MS: number = 120_000

/** The command line, after this package's executable, that runs the configuration probe. */
export const PROBE_COMMAND: readonly string[] = ['probe', 'payload-defaults']

const summariseAccessFindings = (inherited: number, draftReads: number): string =>
  [
    ...(inherited > 0 ? [`${String(inherited)} collection(s) or global(s) inherit Payload's default access`] : []),
    ...(draftReads > 0 ? [`${String(draftReads)} drafts read(s) serve an unapproved document to a stranger`] : []),
  ].join('; ')

/**
 * The probe's outcome, once it ran: a report that was not printed, or not readable, is a failure of its own
 * rather than an empty finding list, because nothing else stands between a crashed probe and a pass.
 * @param result what the probe exited with and printed.
 * @returns the gate result.
 */
export const judgeProbe = (result: RunResult): GateResult => {
  if (result.code !== 0) {
    return failed('the Payload configuration could not be built', asFindings(result.output))
  }
  const report: InheritedAccessReport | undefined = parseInheritedAccessReport(result.stdout)
  const security: PayloadSecurityReport | undefined = parsePayloadSecurityReport(result.stdout)
  if (report === undefined || security === undefined) {
    return failed('the configuration probe printed incomplete or malformed evidence', asFindings(result.output))
  }
  const inherited: readonly string[] = findInheritedAccess(report)
  const draftReads: readonly string[] = findUnconstrainedDraftReads(report)
  const securityFindings: readonly string[] = findPayloadSecurityViolations(security)
  const findings: readonly string[] = [...inherited, ...draftReads, ...securityFindings]
  const summary: string =
    securityFindings.length > 0
      ? 'the resolved Payload security configuration has defects'
      : summariseAccessFindings(inherited.length, draftReads.length)
  return findings.length > 0
    ? failed(summary, findings)
    : passed(
        'every collection and global decides its access, framework-built ones included, no drafts read serves ' +
          'an unapproved document to a stranger, and the production authentication settings are valid',
      )
}

const configFileOf = (root: string): string | undefined => {
  const tsconfig: string = path.join(root, 'tsconfig.json')
  return existsSync(tsconfig) ? path.join(root, payloadConfigPathOf(readJsonFile(tsconfig))) : undefined
}

/**
 * Import the member's built configuration and fail on every operation it leaves to Payload's default.
 * @param target the member being judged.
 * @returns the gate result.
 */
export const payloadDefaults = (target: PayloadMember): GateResult => {
  if (!target.isPayload) {
    return notApplicable('this member is no Payload application')
  }
  const root: string = target.member.root
  const configFile: string | undefined = configFileOf(root)
  if (configFile === undefined || !existsSync(configFile)) {
    return failed('the Payload configuration could not be found', [
      `${target.member.path}: no configuration at ${configFile ?? 'tsconfig.json'}; a Payload member names it under ` +
        'compilerOptions.paths["@payload-config"] in tsconfig.json',
    ])
  }
  const defaultAccessFile: string = path.join(root, 'node_modules', PAYLOAD_PACKAGE, ...DEFAULT_ACCESS_MODULE)
  if (!existsSync(defaultAccessFile)) {
    return failed("Payload's default access could not be located", [
      `${defaultAccessFile} is missing; the layer knows the layout of the Payload version it pins, and this ` +
        'installation differs or is absent',
    ])
  }
  const result: RunResult = runNode(
    '--no-deprecation',
    [
      fileURLToPath(import.meta.resolve('tsx/cli')),
      '--tsconfig',
      path.join(root, 'tsconfig.json'),
      fileURLToPath(new URL('bin.js', import.meta.url)),
      ...PROBE_COMMAND,
      configFile,
      defaultAccessFile,
    ],
    {
      cwd: root,
      env: {
        ...analysisEnvironment(target),
        NODE_ENV: 'production',
      },
      timeoutMs: PROBE_TIMEOUT_MS,
    },
  )
  return withOutput(judgeProbe(result), result.output)
}
