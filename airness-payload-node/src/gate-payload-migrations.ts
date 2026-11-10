// Every schema change the configuration makes is recorded by a committed migration. A probe starts the project's
// Payload without a database and diffs the schema against the newest committed snapshot, as `payload
// migrate:create` does, writing nothing; the gate fails on every statement that command would write now.
import { existsSync } from 'node:fs'
import path from 'node:path'
import type { RunResult } from '@airness/node/exec'
import { failed, type GateResult, notApplicable, passed, withOutput } from '@airness/node/gate-result'
import { payloadConfigFileOf, runConfigProbe } from './config-probe.js'
import { findUnrecordedSchemaChanges, type MigrationDriftReport, parseMigrationDriftReport } from './migration-drift.js'
import { PAYLOAD_PACKAGE, type PayloadMember } from './payload-member.js'
import { asFindings } from './tool-findings.js'

// The entry of the `payload` package, whose default export is the instance `payload migrate:create` starts. Named
// by path for the reason the default-access module is: the stack pins `payload` exactly, and an absent file fails
// closed below.
const PAYLOAD_ENTRY: readonly string[] = ['dist', 'index.js']
// Starting Payload without a database opens no socket, but a plugin might, and a hung start must end as a verdict.
const PROBE_TIMEOUT_MS: number = 180_000

/** The command line, after this package's executable, that runs the migration probe. */
export const MIGRATIONS_PROBE_COMMAND: readonly string[] = ['probe', 'payload-migrations']

/**
 * The probe's outcome, once it ran: a report that was not printed, or not readable, is a failure of its own,
 * because nothing else stands between a crashed probe and a pass.
 * @param result what the probe exited with and printed.
 * @returns the gate result.
 */
const judgeMigrationProbe = (result: RunResult): GateResult => {
  if (result.code !== 0) {
    return failed('the Payload schema could not be compared with the recorded migrations', asFindings(result.output))
  }
  const report: MigrationDriftReport | undefined = parseMigrationDriftReport(result.stdout)
  if (report === undefined) {
    return failed('the migration probe printed incomplete or malformed evidence', asFindings(result.output))
  }
  if (report.kind === 'unsupported') {
    return notApplicable(`the ${report.adapter} database adapter records no schema snapshot to compare`)
  }
  const findings: readonly string[] = findUnrecordedSchemaChanges(report)
  return findings.length > 0
    ? failed('a schema change is recorded by no migration', findings)
    : passed('the committed migrations record the schema the configuration builds')
}

/**
 * Compare the schema the member's configuration builds with the one its committed migrations record.
 * @param target the member being judged.
 * @returns the gate result.
 */
export const payloadMigrations = (target: PayloadMember): GateResult => {
  if (!target.isPayload) {
    return notApplicable('this member is no Payload application')
  }
  const configFile: string | GateResult = payloadConfigFileOf(target)
  if (typeof configFile !== 'string') {
    return configFile
  }
  const payloadFile: string = path.join(target.member.root, 'node_modules', PAYLOAD_PACKAGE, ...PAYLOAD_ENTRY)
  if (!existsSync(payloadFile)) {
    return failed('the Payload runtime could not be located', [
      `${payloadFile} is missing; the layer knows the layout of the Payload version it pins, and this ` +
        'installation differs or is absent',
    ])
  }
  const result: RunResult = runConfigProbe(
    target,
    [...MIGRATIONS_PROBE_COMMAND, configFile, payloadFile],
    PROBE_TIMEOUT_MS,
  )
  return withOutput(judgeMigrationProbe(result), result.output)
}
