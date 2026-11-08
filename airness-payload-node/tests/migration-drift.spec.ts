import { describe, expect, it } from 'vitest'
import {
  findUnrecordedSchemaChanges,
  latestSnapshotIn,
  MIGRATION_DRIFT_REPORT_MARKER,
  type MigrationDriftReport,
  parseMigrationDriftReport,
} from '@/migration-drift.js'

const ADD_COLUMN: string = 'ALTER TABLE "posts" ADD COLUMN "subtitle" varchar;'

const compared = (statements: readonly string[]): MigrationDriftReport => ({
  kind: 'compared',
  directory: 'src/migrations',
  snapshot: '20260101_000000_initial.json',
  statements,
})

const reportLine = (report: unknown): string => `${MIGRATION_DRIFT_REPORT_MARKER}${JSON.stringify(report)}`

describe('latestSnapshotIn', () => {
  // Payload's own choice: the last `.json` in name order, which the timestamp prefix makes the newest.
  it('picks the newest snapshot by name, whatever order the directory lists them in', () => {
    expect(
      latestSnapshotIn([
        '20260301_000000_tags.json',
        '20260101_000000_initial.json',
        '20260301_000000_tags.ts',
        'index.ts',
      ]),
    ).toBe('20260301_000000_tags.json')
  })

  // A migration written from a predefined template carries no snapshot, so the newest snapshot is an older one.
  it('reads past a newer migration that carries no snapshot', () => {
    expect(
      latestSnapshotIn(['20260101_000000_initial.json', '20260101_000000_initial.ts', '20260201_000000_data.ts']),
    ).toBe('20260101_000000_initial.json')
  })

  // A default `sort()` orders by code unit, where every capital precedes every lower-case letter; a locale does not.
  it('orders names by code unit, as the default sort Payload calls does', () => {
    expect(latestSnapshotIn(['20260101_000000_b.json', '20260101_000000_B.json'])).toBe('20260101_000000_b.json')
  })

  it.each([[[]], [['index.ts']], [['20260101_000000_initial.ts', 'index.ts']]])(
    'finds none in a directory that holds no snapshot: %j',
    (names: readonly string[]) => {
      expect(latestSnapshotIn(names)).toBeUndefined()
    },
  )
})

describe('findUnrecordedSchemaChanges', () => {
  it('passes a schema the newest snapshot records', () => {
    expect(findUnrecordedSchemaChanges(compared([]))).toStrictEqual([])
  })

  it('names each unrecorded change by the statement that would record it', () => {
    const findings: readonly string[] = findUnrecordedSchemaChanges(
      compared([ADD_COLUMN, 'ALTER TABLE "_posts_v" ADD COLUMN "version_subtitle" varchar;']),
    )
    expect(findings[0]).toContain('src/migrations/20260101_000000_initial.json')
    expect(findings[0]).toContain('payload migrate:create')
    expect(findings.slice(1)).toStrictEqual([
      `  ${ADD_COLUMN}`,
      '  ALTER TABLE "_posts_v" ADD COLUMN "version_subtitle" varchar;',
    ])
  })

  it('folds a statement that spans lines onto the one line a finding is', () => {
    const findings: readonly string[] = findUnrecordedSchemaChanges(
      compared(['CREATE TABLE "tags" (\n\t"id" serial PRIMARY KEY NOT NULL,\n\t"name" varchar\n);\n']),
    )
    expect(findings[1]).toBe('  CREATE TABLE "tags" ( "id" serial PRIMARY KEY NOT NULL, "name" varchar );')
  })

  // Compared with nothing, every table would read as new, which names the whole schema rather than the change.
  it('reports a directory that holds no snapshot to compare with', () => {
    const findings: readonly string[] = findUnrecordedSchemaChanges({
      kind: 'unsnapshotted',
      directory: 'src/migrations',
    })
    expect(findings).toHaveLength(1)
    expect(findings[0]).toContain('src/migrations holds no schema snapshot')
    expect(findings[0]).toContain('.json')
  })

  it('says nothing about an adapter that keeps no snapshots', () => {
    expect(findUnrecordedSchemaChanges({ kind: 'unsupported', adapter: 'mongoose' })).toStrictEqual([])
  })
})

describe('parseMigrationDriftReport', () => {
  it.each<MigrationDriftReport>([
    compared([ADD_COLUMN]),
    { kind: 'unsnapshotted', directory: 'src/migrations' },
    { kind: 'unsupported', adapter: 'mongoose' },
  ])('reads a $kind report', (report: MigrationDriftReport) => {
    expect(parseMigrationDriftReport(reportLine(report))).toStrictEqual(report)
  })

  // A plugin may log while the configuration loads, even a line that imitates the marker.
  it('reads the last marker line, past what the configuration logged', () => {
    const output: string = [
      'plugin: loaded',
      reportLine({ kind: 'unsnapshotted', directory: 'stale' }),
      reportLine(compared([])),
      '',
    ].join('\n')
    expect(parseMigrationDriftReport(output)).toStrictEqual(compared([]))
  })

  it.each([
    ['no marker line', 'Error: the configuration did not load'],
    ['a marker followed by no JSON', `${MIGRATION_DRIFT_REPORT_MARKER}{`],
    ['an unknown kind', reportLine({ kind: 'skipped' })],
    ['a comparison without statements', reportLine({ kind: 'compared', directory: 'd', snapshot: 's.json' })],
    ['statements that are not text', reportLine({ ...compared([]), statements: [1] })],
    ['a missing directory', reportLine({ kind: 'unsnapshotted' })],
    ['an unnamed adapter', reportLine({ kind: 'unsupported' })],
  ])('is undefined for %s, so the gate fails rather than passes', (_name: string, output: string) => {
    expect(parseMigrationDriftReport(output)).toBeUndefined()
  })
})
