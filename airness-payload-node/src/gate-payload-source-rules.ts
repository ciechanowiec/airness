// The Payload half of the source rules: the Local API used in a way that neither over-fetches nor skips access
// control, every collection and global deciding its access, and every schema change recorded as a migration.
// The language half, which holds in any package, is the node layer's source-rules gate.
import { existsSync, readdirSync } from 'node:fs'
import path from 'node:path'
import type { SpecSource } from '@airness/node/axe-coverage'
import { failed, type GateResult, notApplicable, passed } from '@airness/node/gate-result'
import { stripComments } from '@airness/node/source-text'
import { findBootstrapWrites } from './payload-bootstrap.js'
import {
  declaredMigrationDirectoryIn,
  declaresPushingAdapter,
  findMissingMigrations,
  type MigrationDirectory,
  type MigrationEvidence,
  migrationDirectoriesFor,
} from './payload-database.js'
import type { PayloadMember } from './payload-member.js'
import { findEndpointViolations, findPayloadViolations } from './payload-policy.js'
import type { PayloadViolation } from './payload-source.js'
import { payloadSourcesOf } from './payload-sources.js'
import { findUnguardedRelationships, type LocatedViolation } from './relationship-cleanup.js'

const reported = (file: string, violation: PayloadViolation): string =>
  `${file}:${String(violation.line)} [${violation.rule}] ${violation.reason}`

// Only the files that declare a configuration are read for one.
const configSources = (files: readonly SpecSource[]): readonly string[] =>
  files
    .filter((file: SpecSource): boolean => file.source.includes('buildConfig('))
    .map((file: SpecSource): string => stripComments(file.source))

// The filesystem half of the migration rule. Which directories to read is decided from the configuration in
// payload-database.ts; what they hold is the only thing read here.
const migrationEvidenceOf = (root: string, files: readonly SpecSource[]): MigrationEvidence => {
  const configs: readonly string[] = configSources(files)
  const declared: string | undefined = configs
    .map((code: string): string | undefined => declaredMigrationDirectoryIn(code))
    .find((found: string | undefined): found is string => found !== undefined)
  return {
    declaresPushingAdapter: configs.some((code: string): boolean => declaresPushingAdapter(code)),
    directories: migrationDirectoriesFor(declared).flatMap((relative: string): readonly MigrationDirectory[] => {
      const directory: string = path.join(root, relative)
      return existsSync(directory) ? [{ path: relative, names: readdirSync(directory) }] : []
    }),
  }
}

/**
 * Apply the Payload source rules to every TypeScript file under the member's source roots.
 * @param target the member being judged.
 * @returns the gate result.
 */
export const payloadSourceRules = (target: PayloadMember): GateResult => {
  if (!target.isPayload) {
    return notApplicable('this member is no Payload application')
  }
  const files: readonly SpecSource[] = payloadSourcesOf(target)
  const findings: readonly string[] = [
    ...files.flatMap((file: SpecSource): readonly string[] =>
      [...findPayloadViolations(file.source), ...findEndpointViolations(file.path, file.source)].map(
        (violation: PayloadViolation): string => reported(file.path, violation),
      ),
    ),
    // A required relationship names its target by slug, declared in another file, so these read the whole set
    // and report the file that declares the relationship.
    ...[...findUnguardedRelationships(files), ...findBootstrapWrites(files)].map((located: LocatedViolation): string =>
      reported(located.path, located.violation),
    ),
    ...findMissingMigrations(migrationEvidenceOf(target.member.root, files)),
  ]
  return findings.length > 0
    ? failed(`${String(findings.length)} Payload source rule violation(s)`, findings)
    : passed(`${String(files.length)} source file(s) follow the Payload source rules`)
}
