// Every collection and global in the BUILT configuration decides its access.
//
// The static rules read the access blocks a project wrote; this gate imports the configuration Payload actually
// boots, so the collections the framework builds for the project (the folder tree, the job queue, a plugin's
// own) are judged too. Each of those arrives with Payload's default, which admits every signed-in user, and the
// anonymous sweep cannot see that because a signed-in user is not anonymous. The same import shows where every
// upload collection stores its files once every storage adapter has run, which no source rule can read, whether
// it keeps what a camera wrote into an uploaded picture, and what each auth collection resolves its session cookie
// to in production, which a login the anonymous sweep never makes is the only other way to observe. It also shows
// whether each entity that keeps drafts enables Live Preview, which the configuration enables at two levels.
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
import { findMissingLivePreview, type LivePreviewReport, parseLivePreviewReport } from './payload-live-preview.js'
import { PAYLOAD_PACKAGE, type PayloadMember } from './payload-member.js'
import { findKeptPictureMetadata, type PictureHandlingReport, parsePictureHandlingReport } from './payload-pictures.js'
import {
  findPayloadSecurityViolations,
  type PayloadSecurityReport,
  parsePayloadSecurityReport,
} from './payload-security.js'
import type { PayloadSettings } from './payload-settings.js'
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
  readonly pictures: number
  readonly livePreview: number
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
    ...(counts.pictures > 0 ? [`${String(counts.pictures)} upload collection(s) keep what a camera wrote`] : []),
    ...(counts.livePreview > 0
      ? [`${String(counts.livePreview)} collection(s) or global(s) keep drafts without a Live Preview`]
      : []),
  ].join('; ')

/** Every half of the report the probe prints, each read. */
interface ProbeEvidence {
  readonly report: InheritedAccessReport
  readonly security: PayloadSecurityReport
  readonly storage: UploadStorageReport
  readonly pictures: PictureHandlingReport
  readonly livePreview: LivePreviewReport
}

/**
 * Read every half of the probe's report. A half that is missing or malformed leaves no evidence at all, so the gate
 * fails rather than judging part of the configuration.
 * @param stdout the probe's standard output.
 * @returns every half, or undefined when any one was not printed readably.
 */
const evidenceOf = (stdout: string): ProbeEvidence | undefined => {
  const report: InheritedAccessReport | undefined = parseInheritedAccessReport(stdout)
  const security: PayloadSecurityReport | undefined = parsePayloadSecurityReport(stdout)
  const storage: UploadStorageReport | undefined = parseUploadStorageReport(stdout)
  const pictures: PictureHandlingReport | undefined = parsePictureHandlingReport(stdout)
  const livePreview: LivePreviewReport | undefined = parseLivePreviewReport(stdout)
  return report === undefined ||
    security === undefined ||
    storage === undefined ||
    pictures === undefined ||
    livePreview === undefined
    ? undefined
    : { report, security, storage, pictures, livePreview }
}

/**
 * The probe's outcome, once it ran: a report that was not printed, or not readable, is a failure of its own
 * rather than an empty finding list, because nothing else stands between a crashed probe and a pass.
 * @param result what the probe exited with and printed.
 * @returns the gate result.
 */
const judgeProbe = (
  result: RunResult,
  declared: Pick<PayloadSettings, 'localStorage' | 'keptMetadata'>,
): GateResult => {
  if (result.code !== 0) {
    return failed('the Payload configuration could not be built', asFindings(result.output))
  }
  const evidence: ProbeEvidence | undefined = evidenceOf(result.stdout)
  if (evidence === undefined) {
    return failed('the configuration probe printed incomplete or malformed evidence', asFindings(result.output))
  }
  const { report, security, storage, pictures, livePreview }: ProbeEvidence = evidence
  const inherited: readonly string[] = findInheritedAccess(report)
  const draftReads: readonly string[] = findUnconstrainedDraftReads(report)
  const securityFindings: readonly string[] = findPayloadSecurityViolations(security)
  const storageFindings: readonly string[] = findLocalUploadStorage(storage, declared.localStorage)
  const pictureFindings: readonly string[] = findKeptPictureMetadata(pictures, declared.keptMetadata)
  const livePreviewFindings: readonly string[] = findMissingLivePreview(livePreview)
  const findings: readonly string[] = [
    ...inherited,
    ...draftReads,
    ...securityFindings,
    ...storageFindings,
    ...pictureFindings,
    ...livePreviewFindings,
  ]
  const summary: string =
    securityFindings.length > 0
      ? 'the resolved Payload security configuration has defects'
      : summariseFindings({
          inherited: inherited.length,
          draftReads: draftReads.length,
          storage: storageFindings.length,
          pictures: pictureFindings.length,
          livePreview: livePreviewFindings.length,
        })
  return findings.length > 0
    ? failed(summary, findings)
    : passed(
        'every collection and global decides its access, framework-built ones included, no drafts read serves ' +
          'an unapproved document to a stranger, the production authentication settings are valid and mark ' +
          'every session cookie Secure, no upload collection stores its files on the local disk, none keeps ' +
          'what a camera wrote into a picture, unless the project declares it, and every collection and global ' +
          'that keeps drafts shows its page beside the editing form through Live Preview',
      )
}

/**
 * Import the member's built configuration and fail on every operation it leaves to Payload's default, and on
 * every upload collection it stores on the local disk or keeps a picture's metadata in without a declaration, and on
 * every entity that keeps drafts without a Live Preview.
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
  return withOutput(judgeProbe(result, target.payload), result.output)
}
