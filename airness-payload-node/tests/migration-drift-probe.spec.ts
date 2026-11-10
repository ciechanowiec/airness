import { readdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { type MigrationDriftReport, parseMigrationDriftReport } from '@/migration-drift.js'
import { migrationDriftProbe } from '@/migration-drift-probe.js'
import { projectForTest } from './helpers/temporary-project.js'

// A stand-in for drizzle-kit's API: a schema is its own snapshot, and a migration lists every table the newer
// snapshot holds that the older one does not. A snapshot of the older format keeps its tables under another key,
// which only the upgrade reads, so comparing one without upgrading it first fails.
interface Snapshot {
  readonly version: string
  readonly tables: readonly string[]
}

const CURRENT_VERSION: string = '7'

const asSnapshot = (value: unknown): Snapshot => value as Snapshot

const fakeKit = (): Record<string, unknown> => ({
  generateDrizzleJson: (schema: unknown): unknown => schema,
  generateMigration: async (before: unknown, after: unknown): Promise<readonly string[]> => {
    await Promise.resolve()
    return asSnapshot(after)
      .tables.filter((table: string): boolean => !asSnapshot(before).tables.includes(table))
      .map((table: string): string => `CREATE TABLE "${table}" ();`)
  },
  upSnapshot: (snapshot: unknown): Snapshot => {
    const legacy: unknown = Reflect.get(snapshot as object, 'legacyTables')
    if (!Array.isArray(legacy)) {
      throw new TypeError('upgraded a snapshot that is already current')
    }
    return { version: CURRENT_VERSION, tables: legacy.map(String) }
  },
})

const CURRENT: Snapshot = { version: CURRENT_VERSION, tables: ['posts', 'users'] }

const adapterAt = (root: string, kit: Record<string, unknown> = fakeKit()): unknown => ({
  migrationDir: path.join(root, 'src', 'migrations'),
  schema: CURRENT,
  requireDrizzleKit: (): unknown => kit,
})

const snapshot = (tables: readonly string[]): string => JSON.stringify({ version: CURRENT_VERSION, tables })

// A stand-in for the `payload` package's entry: an instance that, once started, carries the adapter its
// configuration names. It records the options it was started with, so the spec can hold it to no connection.
const STARTED: string = 'airnessPayloadStartedWith'

const FAKE_PAYLOAD: string = [
  'export default {',
  '  async init(options) {',
  `    globalThis.${STARTED} = options`,
  '    this.db = (await options.config).db',
  '  },',
  '}',
].join('\n')

// The probe starts that stand-in with a configuration whose adapter is the one a spec hands over, so each
// spec states its adapter as an object while the probe still starts, compares and reports it end to end.
const ADAPTER: string = 'airnessProbeAdapter'

const driftOf = async (root: string, adapter: unknown): Promise<MigrationDriftReport | undefined> => {
  Reflect.set(globalThis, ADAPTER, adapter)
  writeFileSync(path.join(root, 'payload.mjs'), FAKE_PAYLOAD)
  writeFileSync(path.join(root, 'adapter.config.mjs'), `export default { db: globalThis.${ADAPTER} }`)
  return parseMigrationDriftReport(
    await migrationDriftProbe(path.join(root, 'adapter.config.mjs'), path.join(root, 'payload.mjs')),
  )
}

// The probe reports the directory relative to the working directory it runs in, the member's own.
const migrationsOf = (root: string): string => path.relative(process.cwd(), path.join(root, 'src', 'migrations'))

describe('migrationDriftProbe over a started adapter', () => {
  it('reports no statement when the newest snapshot records the schema', async () => {
    const root: string = projectForTest({
      'src/migrations/20260101_000000_initial.ts': '',
      'src/migrations/20260101_000000_initial.json': snapshot(['posts', 'users']),
    })
    expect(await driftOf(root, adapterAt(root))).toStrictEqual({
      kind: 'compared',
      directory: migrationsOf(root),
      snapshot: '20260101_000000_initial.json',
      statements: [],
    })
  })

  // The defect the gate exists for: a field added in the configuration, and no migration that records it.
  it('reports the statement a schema change without a migration needs, comparing the newest snapshot', async () => {
    const root: string = projectForTest({
      'src/migrations/20260101_000000_initial.json': snapshot([]),
      'src/migrations/20260201_000000_posts.json': snapshot(['posts']),
    })
    const report: MigrationDriftReport | undefined = await driftOf(root, adapterAt(root))
    expect(report).toMatchObject({ snapshot: '20260201_000000_posts.json', statements: ['CREATE TABLE "users" ();'] })
  })

  it('upgrades a snapshot recorded in an older format before comparing, as Payload does', async () => {
    const root: string = projectForTest({
      'src/migrations/20260101_000000_initial.json': JSON.stringify({ version: '5', legacyTables: ['posts'] }),
    })
    const report: MigrationDriftReport | undefined = await driftOf(root, adapterAt(root))
    expect(report).toMatchObject({ statements: ['CREATE TABLE "users" ();'] })
  })

  // The stand-in's upgrade throws on a current snapshot, so a comparison that upgraded one would fail here.
  it('compares a snapshot of the current format as it is', async () => {
    const root: string = projectForTest({ 'src/migrations/20260101_000000_initial.json': snapshot(['posts']) })
    expect(await driftOf(root, adapterAt(root))).toMatchObject({ statements: ['CREATE TABLE "users" ();'] })
  })

  it('compares as it is a snapshot whose adapter offers no upgrade', async () => {
    const root: string = projectForTest({ 'src/migrations/20260101_000000_initial.json': snapshot(['posts', 'users']) })
    const kit: Record<string, unknown> = { ...fakeKit(), upSnapshot: undefined }
    expect(await driftOf(root, adapterAt(root, kit))).toMatchObject({ statements: [] })
  })

  it('reports a migration directory that holds migrations but no snapshot', async () => {
    const root: string = projectForTest({
      'src/migrations/20260101_000000_initial.ts': '',
      'src/migrations/index.ts': '',
    })
    expect(await driftOf(root, adapterAt(root))).toStrictEqual({
      kind: 'unsnapshotted',
      directory: migrationsOf(root),
    })
  })

  it('reports a migration directory that does not exist', async () => {
    const root: string = projectForTest({})
    expect(await driftOf(root, adapterAt(root))).toMatchObject({ kind: 'unsnapshotted' })
  })

  it('writes nothing into the migration directory', async () => {
    const root: string = projectForTest({ 'src/migrations/20260101_000000_initial.json': snapshot([]) })
    await driftOf(root, adapterAt(root))
    expect(readdirSync(path.join(root, 'src', 'migrations'))).toStrictEqual(['20260101_000000_initial.json'])
  })

  it.each([
    ['by its package name', { packageName: '@payloadcms/db-mongodb', name: 'mongoose' }, '@payloadcms/db-mongodb'],
    ['by its name', { name: 'mongoose' }, 'mongoose'],
  ])('reports an adapter that keeps no snapshots %s', async (_how: string, adapter: unknown, named: string) => {
    expect(await driftOf(projectForTest({}), adapter)).toStrictEqual({ kind: 'unsupported', adapter: named })
  })

  it('fails when the adapter hands out no drizzle-kit', async () => {
    const root: string = projectForTest({ 'src/migrations/20260101_000000_initial.json': snapshot([]) })
    const adapter: unknown = { ...(adapterAt(root) as object), requireDrizzleKit: (): unknown => ({}) }
    await expect(driftOf(root, adapter)).rejects.toThrow('did not hand out drizzle-kit')
  })

  it('fails when drizzle-kit returns no statement list', async () => {
    const root: string = projectForTest({ 'src/migrations/20260101_000000_initial.json': snapshot([]) })
    const kit: Record<string, unknown> = { ...fakeKit(), generateMigration: (): unknown => 'none' }
    await expect(driftOf(root, adapterAt(root, kit))).rejects.toThrow('no statement list')
  })
})

const fakeConfig = (root: string, tables: readonly string[]): string =>
  [
    'const kit = {',
    '  generateDrizzleJson: (schema) => schema,',
    '  generateMigration: (before, after) =>',
    "    after.tables.filter((table) => !before.tables.includes(table)).map((table) => 'CREATE TABLE ' + table),",
    '}',
    'export default Promise.resolve({',
    '  db: {',
    `    migrationDir: ${JSON.stringify(path.join(root, 'src', 'migrations'))},`,
    `    schema: ${snapshot(tables)},`,
    '    requireDrizzleKit: () => kit,',
    '  },',
    '})',
  ].join('\n')

describe('migrationDriftProbe', () => {
  it('starts the installed Payload without a database and prints the report the gate parses', async () => {
    const root: string = projectForTest({
      'payload.mjs': FAKE_PAYLOAD,
      'src/migrations/20260101_000000_initial.json': snapshot(['posts']),
    })
    writeFileSync(path.join(root, 'payload.config.mjs'), fakeConfig(root, ['posts', 'tags']))
    const output: string = await migrationDriftProbe(
      path.join(root, 'payload.config.mjs'),
      path.join(root, 'payload.mjs'),
    )
    const report: MigrationDriftReport | undefined = parseMigrationDriftReport(output)
    expect(report).toMatchObject({ kind: 'compared', statements: ['CREATE TABLE tags'] })
    expect(report?.kind === 'compared' ? report.directory : '').toMatch(/src\/migrations$/u)
    expect(Reflect.get(globalThis, STARTED)).toMatchObject({ disableDBConnect: true, disableOnInit: true })
  })

  it('fails when the entry exports no Payload instance to start', async () => {
    const root: string = projectForTest({
      'payload.mjs': 'export default {}',
      'payload.config.mjs': 'export default {}',
    })
    await expect(
      migrationDriftProbe(path.join(root, 'payload.config.mjs'), path.join(root, 'payload.mjs')),
    ).rejects.toThrow('exports no Payload instance to start')
  })
})
