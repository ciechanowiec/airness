// Whether the committed migrations record the schema the configuration builds.
//
// The source rule in payload-database.ts asks only whether a migration exists, so a field added without one
// passed every gate and the production boot then failed on the missing column. Payload's drizzle adapters
// answer the real question without a database: `payload migrate:create` diffs the schema the configuration
// builds against the newest committed `.json` snapshot, and has nothing to write when the two agree. The
// probe repeats that comparison read-only and reports what it found; this module decides what that means.
import { asOptionalText, isArray, readKey } from '@airness/node/json-shapes'

/** The prefix the probe prints its report behind, so a plugin logging while the configuration loads cannot hide it. */
export const MIGRATION_DRIFT_REPORT_MARKER: string = 'airness-payload-migration-drift:'

/**
 * What the probe found, by member-relative migration directory.
 *
 * `unsupported` is an adapter that keeps no schema snapshot to compare, `unsnapshotted` a directory that holds
 * none, and `compared` the statements `payload migrate:create` would write now: empty when the committed
 * migrations record the schema.
 */
export type MigrationDriftReport =
  | { readonly kind: 'unsupported'; readonly adapter: string }
  | { readonly kind: 'unsnapshotted'; readonly directory: string }
  | {
      readonly kind: 'compared'
      readonly directory: string
      readonly snapshot: string
      readonly statements: readonly string[]
    }

const SNAPSHOT_EXTENSION: string = '.json'

// The order of a default `sort()`, which Payload's choice relies on: by UTF-16 code unit, not by locale.
const byCodeUnit = (left: string, right: string): number => Number(left > right) - Number(left < right)

/**
 * The snapshot Payload compares against, chosen the way its `createMigration` chooses it: the last `.json`
 * file of the directory in name order. A migration written from a predefined template carries no snapshot of
 * its own, so the newest snapshot need not belong to the newest migration.
 * @param names the entries of the migration directory.
 * @returns the snapshot's file name, or undefined when the directory holds none.
 */
export const latestSnapshotIn = (names: readonly string[]): string | undefined =>
  names
    .filter((name: string): boolean => name.endsWith(SNAPSHOT_EXTENSION))
    .toSorted(byCodeUnit)
    .at(-1)

const REPAIR: string =
  'run `payload migrate:create <name>` and commit the migration and the `.json` snapshot it writes beside it'

// A statement drizzle writes spans lines; a finding is one line, so the statement's own whitespace is folded.
const oneLine = (statement: string): string => statement.replaceAll(/\s+/gu, ' ').trim()

/**
 * Every finding a report carries: the schema changes no migration records, each named by the SQL that would
 * record it, or the absence of a snapshot to compare with.
 * @param report the probe's report.
 * @returns the findings, empty when the committed migrations record the schema or the adapter keeps none.
 */
export const findUnrecordedSchemaChanges = (report: MigrationDriftReport): readonly string[] => {
  if (report.kind === 'unsupported') {
    return []
  }
  if (report.kind === 'unsnapshotted') {
    return [
      `${report.directory} holds no schema snapshot, so no committed file records the schema the configuration ` +
        `builds and every table would read as new; ${REPAIR}`,
    ]
  }
  if (report.statements.length === 0) {
    return []
  }
  return [
    `the configuration's schema differs from ${report.directory}/${report.snapshot}, the newest recorded ` +
      `snapshot, so a production boot runs against a schema no migration creates; ${REPAIR}. The unrecorded ` +
      'change, as the migration would write it:',
    ...report.statements.map((statement: string): string => `  ${oneLine(statement)}`),
  ]
}

const asStatements = (value: unknown): readonly string[] | undefined =>
  isArray(value) && value.every((statement: unknown): boolean => typeof statement === 'string')
    ? value.map(String)
    : undefined

type ReportReader = (parsed: unknown) => MigrationDriftReport | undefined

const unsupportedOf: ReportReader = (parsed: unknown) => {
  const adapter: string | undefined = asOptionalText(readKey(parsed, 'adapter'))
  return adapter === undefined ? undefined : { kind: 'unsupported', adapter }
}

const unsnapshottedOf: ReportReader = (parsed: unknown) => {
  const directory: string | undefined = asOptionalText(readKey(parsed, 'directory'))
  return directory === undefined ? undefined : { kind: 'unsnapshotted', directory }
}

const comparedOf: ReportReader = (parsed: unknown) => {
  const directory: string | undefined = asOptionalText(readKey(parsed, 'directory'))
  const snapshot: string | undefined = asOptionalText(readKey(parsed, 'snapshot'))
  const statements: readonly string[] | undefined = asStatements(readKey(parsed, 'statements'))
  return directory === undefined || snapshot === undefined || statements === undefined
    ? undefined
    : { kind: 'compared', directory, snapshot, statements }
}

const READERS: ReadonlyMap<unknown, ReportReader> = new Map<unknown, ReportReader>([
  ['unsupported', unsupportedOf],
  ['unsnapshotted', unsnapshottedOf],
  ['compared', comparedOf],
])

const reportOf = (parsed: unknown): MigrationDriftReport | undefined => READERS.get(readKey(parsed, 'kind'))?.(parsed)

const parseJson = (text: string): unknown => {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

/**
 * Read the probe's report out of everything it wrote to standard output.
 *
 * The LAST marker line is the report, and one that does not parse or has the wrong shape is undefined rather
 * than empty: a probe that printed a stack trace must fail the gate, never pass it as "no drift".
 * @param text the probe's standard output.
 * @returns the report, or undefined when no well-formed report was printed.
 */
export const parseMigrationDriftReport = (text: string): MigrationDriftReport | undefined => {
  const line: string | undefined = text
    .split('\n')
    .map((candidate: string): string => candidate.trim())
    .findLast((candidate: string): boolean => candidate.startsWith(MIGRATION_DRIFT_REPORT_MARKER))
  return line === undefined ? undefined : reportOf(parseJson(line.slice(MIGRATION_DRIFT_REPORT_MARKER.length)))
}
