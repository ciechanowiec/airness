// Every collection and global in the BUILT configuration decides its access.
//
// The static rules read the access blocks a project wrote; this gate imports the configuration Payload actually
// boots, so the collections the framework builds for the project (the folder tree, the job queue, a plugin's
// own) are judged too. Each of those arrives with Payload's default, which admits every signed-in user, and the
// anonymous sweep cannot see that because a signed-in user is not anonymous. The same import shows where every
// upload collection stores its files once every storage adapter has run, which no source rule can read.
import { existsSync } from 'node:fs'
import path from 'node:path'
import type { RunResult } from '@airness/node/exec'
import { failed, type GateResult, notApplicable, passed, withOutput } from '@airness/node/gate-result'
import { payloadConfigFileOf, runConfigProbe } from './config-probe.js'
import {
  findInheritedAccess,
  findUnconstrainedDraftReads,
  type InheritedAccessReport,
  parseInheritedAccessReport,
} from './payload-defaults.js'
import { PAYLOAD_PACKAGE, type PayloadMember } from './payload-member.js'
import {
  findPayloadSecurityViolations,
  type PayloadSecurityReport,
  parsePayloadSecurityReport,
} from './payload-security.js'
import type { LocalStorage } from './payload-settings.js'
import { findLocalUploadStorage, parseUploadStorageReport, type UploadStorageReport } from './payload-storage.js'
import { asFindings } from './tool-findings.js'

// Where Payload keeps the function it fills an undeclared operation with. Named by path because the package's
// exports map does not expose it; the stack pins `payload` exactly, so a layout change arrives with an
// @airness/payload release, and an absent file fails closed below.
const DEFAULT_ACCESS_MODULE: readonly string[] = ['dist', 'auth', 'defaultAccess.js']
// Building a configuration opens no socket, but a plugin might, and a hung import must end as a verdict.
const PROBE_TIMEOUT_MS: number = 120_000

/** The command line, after this package's executable, that runs the configuration probe. */
export const PROBE_COMMAND: readonly string[] = ['probe', 'payload-defaults']

/** How many findings of each access and storage kind the probe's evidence produced. */
interface FindingCounts {
  readonly inherited: number
  readonly draftReads: number
  readonly storage: number
}

const summariseFindings = (counts: FindingCounts): string =>
  [
    ...(counts.inherited > 0
      ? [`${String(counts.inherited)} collection(s) or global(s) inherit Payload's default access`]
      : []),
    ...(counts.draftReads > 0
      ? [`${String(counts.draftReads)} drafts read(s) serve an unapproved document to a stranger`]
      : []),
    ...(counts.storage > 0 ? [`${String(counts.storage)} upload storage defect(s)`] : []),
  ].join('; ')

/**
 * The probe's outcome, once it ran: a report that was not printed, or not readable, is a failure of its own
 * rather than an empty finding list, because nothing else stands between a crashed probe and a pass.
 * @param result what the probe exited with and printed.
 * @returns the gate result.
 */
const judgeProbe = (result: RunResult, localStorage: readonly LocalStorage[]): GateResult => {
  if (result.code !== 0) {
    return failed('the Payload configuration could not be built', asFindings(result.output))
  }
  const report: InheritedAccessReport | undefined = parseInheritedAccessReport(result.stdout)
  const security: PayloadSecurityReport | undefined = parsePayloadSecurityReport(result.stdout)
  const storage: UploadStorageReport | undefined = parseUploadStorageReport(result.stdout)
  if (report === undefined || security === undefined || storage === undefined) {
    return failed('the configuration probe printed incomplete or malformed evidence', asFindings(result.output))
  }
  const inherited: readonly string[] = findInheritedAccess(report)
  const draftReads: readonly string[] = findUnconstrainedDraftReads(report)
  const securityFindings: readonly string[] = findPayloadSecurityViolations(security)
  const storageFindings: readonly string[] = findLocalUploadStorage(storage, localStorage)
  const findings: readonly string[] = [...inherited, ...draftReads, ...securityFindings, ...storageFindings]
  const summary: string =
    securityFindings.length > 0
      ? 'the resolved Payload security configuration has defects'
      : summariseFindings({
          inherited: inherited.length,
          draftReads: draftReads.length,
          storage: storageFindings.length,
        })
  return findings.length > 0
    ? failed(summary, findings)
    : passed(
        'every collection and global decides its access, framework-built ones included, no drafts read serves ' +
          'an unapproved document to a stranger, the production authentication settings are valid, and no ' +
          'upload collection stores its files on the local disk unless the project declares it',
      )
}

/**
 * Import the member's built configuration and fail on every operation it leaves to Payload's default, and on
 * every upload collection it stores on the local disk without a declaration.
 * @param target the member being judged.
 * @returns the gate result.
 */
export const payloadDefaults = (target: PayloadMember): GateResult => {
  if (!target.isPayload) {
    return notApplicable('this member is no Payload application')
  }
  const configFile: string | GateResult = payloadConfigFileOf(target)
  if (typeof configFile !== 'string') {
    return configFile
  }
  const defaultAccessFile: string = path.join(
    target.member.root,
    'node_modules',
    PAYLOAD_PACKAGE,
    ...DEFAULT_ACCESS_MODULE,
  )
  if (!existsSync(defaultAccessFile)) {
    return failed("Payload's default access could not be located", [
      `${defaultAccessFile} is missing; the layer knows the layout of the Payload version it pins, and this ` +
        'installation differs or is absent',
    ])
  }
  const result: RunResult = runConfigProbe(target, [...PROBE_COMMAND, configFile, defaultAccessFile], PROBE_TIMEOUT_MS)
  return withOutput(judgeProbe(result, target.payload.localStorage), result.output)
}
