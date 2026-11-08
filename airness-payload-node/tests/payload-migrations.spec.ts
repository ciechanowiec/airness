import path from 'node:path'
import type { GateResult } from '@airness/node/gate-result'
import { describe, expect, it } from 'vitest'
import { runCommandLine } from '@/command-line.js'
import { probeArguments } from '@/config-probe.js'
import { judgeMigrationProbe, MIGRATIONS_PROBE_COMMAND, payloadMigrations } from '@/gate-payload-migrations.js'
import { MIGRATION_DRIFT_REPORT_MARKER, type MigrationDriftReport } from '@/migration-drift.js'
import type { PayloadMember } from '@/payload-member.js'
import { REQUIRED_PINS } from '@/payload-pins.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { payloadMemberAt, projectForTest } from './helpers/temporary-project.js'

const PAYLOAD_MANIFEST: unknown = { name: 'site', dependencies: { ...REQUIRED_PINS } }

const probed = (report: MigrationDriftReport): GateResult => {
  const line: string = `${MIGRATION_DRIFT_REPORT_MARKER}${JSON.stringify(report)}\n`
  return judgeMigrationProbe({ code: 0, output: line, stdout: line })
}

describe('judgeMigrationProbe', () => {
  it('passes a schema the committed migrations record', () => {
    const result: GateResult = probed({
      kind: 'compared',
      directory: 'src/migrations',
      snapshot: '20260101_000000_initial.json',
      statements: [],
    })
    expect(result.ok).toBe(true)
    expect(result.summary).toBe('the committed migrations record the schema the configuration builds')
  })

  it('fails on a schema change no migration records, naming the change', () => {
    const result: GateResult = probed({
      kind: 'compared',
      directory: 'src/migrations',
      snapshot: '20260101_000000_initial.json',
      statements: ['ALTER TABLE "posts" ADD COLUMN "subtitle" varchar;'],
    })
    expect(result.ok).toBe(false)
    expect(result.summary).toBe('a schema change is recorded by no migration')
    expect(result.findings).toContain('  ALTER TABLE "posts" ADD COLUMN "subtitle" varchar;')
  })

  it('fails on a migration directory that holds no snapshot', () => {
    const result: GateResult = probed({ kind: 'unsnapshotted', directory: 'src/migrations' })
    expect(result.ok).toBe(false)
    expect(result.findings[0]).toContain('holds no schema snapshot')
  })

  it('does not apply to an adapter that keeps no snapshots', () => {
    const result: GateResult = probed({ kind: 'unsupported', adapter: 'mongoose' })
    expect(result.isApplicable).toBe(false)
    expect(result.summary).toContain('mongoose')
  })

  it('fails a probe that could not start the configuration, with its output', () => {
    const result: GateResult = judgeMigrationProbe({ code: 1, output: 'Error: no secret', stdout: '' })
    expect(result.ok).toBe(false)
    expect(result.summary).toBe('the Payload schema could not be compared with the recorded migrations')
    expect(result.findings).toContain('Error: no secret')
  })

  it('fails a probe that printed no readable report', () => {
    expect(judgeMigrationProbe({ code: 0, output: 'nothing', stdout: 'nothing' }).summary).toBe(
      'the migration probe printed incomplete or malformed evidence',
    )
  })
})

describe('the migration probe command', () => {
  // The gate runs the probe through tsx with the member's tsconfig, then hands it the two paths it imports.
  it('runs the executable through tsx with the member tsconfig, then the probe and its two paths', () => {
    const probe: readonly string[] = [...MIGRATIONS_PROBE_COMMAND, '/site/src/payload.config.ts', '/site/index.js']
    const commandLine: readonly string[] = probeArguments('/site', probe)
    expect(commandLine[0]).toMatch(/tsx[/\\]dist[/\\]cli\.mjs$/u)
    expect(commandLine.slice(1, 3)).toStrictEqual(['--tsconfig', path.join('/site', 'tsconfig.json')])
    expect(path.basename(String(commandLine[3]))).toBe('bin.js')
    expect(commandLine.slice(4)).toStrictEqual([
      'probe',
      'payload-migrations',
      '/site/src/payload.config.ts',
      '/site/index.js',
    ])
  })
})

describe('payload-migrations', PROCESS_TIME, () => {
  it('does not apply to a member that is no Payload application', () => {
    const root: string = projectForTest({})
    expect(payloadMigrations(payloadMemberAt(root, { name: 'library' })).isApplicable).toBe(false)
  })

  it('fails when the member names no configuration that exists', () => {
    const root: string = projectForTest({ 'tsconfig.json': '{}' })
    expect(payloadMigrations(payloadMemberAt(root, PAYLOAD_MANIFEST)).summary).toBe(
      'the Payload configuration could not be found',
    )
  })

  it('fails when the installed Payload cannot be located', () => {
    const root: string = projectForTest({ 'tsconfig.json': '{}', 'src/payload.config.ts': 'export default {}' })
    const target: PayloadMember = payloadMemberAt(root, PAYLOAD_MANIFEST)
    const result: GateResult = payloadMigrations(target)
    expect(result.summary).toBe('the Payload runtime could not be located')
    expect(result.findings[0]).toContain(path.join('node_modules', 'payload', 'dist', 'index.js'))
  })
})

// A member with a stand-in for the installed `payload` package, whose adapter compares snapshots the way the
// drizzle adapters do: a schema is its own snapshot, and a migration lists every table the newer one adds.
const memberWith = (schema: readonly string[], recorded: readonly string[]): PayloadMember => {
  const root: string = projectForTest({
    'tsconfig.json': '{}',
    'node_modules/payload/package.json': JSON.stringify({ name: 'payload', type: 'module' }),
    'node_modules/payload/dist/index.js':
      'export default { async init(options) { this.db = (await options.config).db } }',
    'src/payload.config.ts': [
      "import path from 'node:path'",
      'const kit = {',
      '  generateDrizzleJson: (schema: { tables: string[] }) => schema,',
      '  generateMigration: (before: { tables: string[] }, after: { tables: string[] }) =>',
      "    after.tables.filter((table) => !before.tables.includes(table)).map((table) => 'CREATE TABLE ' + table),",
      '}',
      'export default {',
      "  db: { migrationDir: path.resolve('src/migrations'), schema: " +
        `${JSON.stringify({ version: '7', tables: schema })}, requireDrizzleKit: () => kit },`,
      '}',
    ].join('\n'),
    'src/migrations/20260101_000000_initial.ts': '',
    'src/migrations/20260101_000000_initial.json': JSON.stringify({ version: '7', tables: recorded }),
  })
  return payloadMemberAt(root, PAYLOAD_MANIFEST)
}

describe('payload-migrations against a started configuration', PROCESS_TIME, () => {
  it('passes a member whose newest snapshot records its schema', () => {
    const result: GateResult = payloadMigrations(memberWith(['posts'], ['posts']))
    expect(result.summary).toBe('the committed migrations record the schema the configuration builds')
  })

  it('fails a member that changed its schema without a migration, naming the change', () => {
    const result: GateResult = payloadMigrations(memberWith(['posts', 'tags'], ['posts']))
    expect(result.ok).toBe(false)
    expect(result.findings).toContain('  CREATE TABLE tags')
  })
})

describe('the payload-migrations probe on the command line', PROCESS_TIME, () => {
  it('refuses a probe invocation without both paths', async () => {
    expect(await runCommandLine([...MIGRATIONS_PROBE_COMMAND, 'config.ts'], '.')).toBe(1)
  })

  it('prints the report of the probe', async () => {
    const root: string = projectForTest({
      'payload.mjs': 'export default { async init() { this.db = { name: "mongoose" } } }\n',
      'config.mjs': 'export default {}\n',
    })
    expect(
      await runCommandLine(
        [...MIGRATIONS_PROBE_COMMAND, path.join(root, 'config.mjs'), path.join(root, 'payload.mjs')],
        root,
      ),
    ).toBe(0)
  })
})
