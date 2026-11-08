// The half of the `payload-migrations` gate that runs inside the project. It starts the project's Payload the way
// `payload migrate:create` does, with no database connection, and repeats that command's comparison: the schema
// the configuration builds against the newest committed `.json` snapshot, through the drizzle-kit the adapter
// itself loads. It writes nothing - not the migration, not the snapshot, not the index - and prints the
// marker-prefixed report migration-drift.ts parses. Nothing here imports `payload` by name: the copy that
// matters is the one the project installed, which the gate hands over by path.
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { isArray, readKey } from '@airness/node/json-shapes'
import { latestSnapshotIn, MIGRATION_DRIFT_REPORT_MARKER, type MigrationDriftReport } from './migration-drift.js'

/** The part of Payload's instance this probe starts. */
interface StartablePayload {
  readonly init: (options: Readonly<Record<string, unknown>>) => Promise<unknown>
}

/** The functions of drizzle-kit's API the adapter hands out, as `createMigration` uses them. */
interface DrizzleKit {
  readonly generateDrizzleJson: (schema: unknown) => unknown
  readonly generateMigration: (before: unknown, after: unknown) => unknown
  readonly upSnapshot?: (snapshot: unknown) => unknown
}

const isStartable = (value: unknown): value is StartablePayload => typeof readKey(value, 'init') === 'function'

const isDrizzleKit = (value: unknown): value is DrizzleKit =>
  typeof readKey(value, 'generateDrizzleJson') === 'function' &&
  typeof readKey(value, 'generateMigration') === 'function'

const versionOf = (snapshot: unknown): number => Number(readKey(snapshot, 'version'))

const statementsOf = (value: unknown): readonly string[] => {
  if (!isArray(value)) {
    throw new TypeError('drizzle-kit returned no statement list')
  }
  return value.map(String)
}

// The adapter's own drizzle-kit, asked as the adapter asks it: a migration recorded under an older snapshot
// version is upgraded first, or every table would differ by format alone.
const compare = async (adapter: unknown, directory: string, snapshot: string): Promise<readonly string[]> => {
  const requireKit: unknown = readKey(adapter, 'requireDrizzleKit')
  const kit: unknown = typeof requireKit === 'function' ? Reflect.apply(requireKit, adapter, []) : undefined
  if (!isDrizzleKit(kit)) {
    throw new TypeError('the database adapter did not hand out drizzle-kit')
  }
  const current: unknown = await kit.generateDrizzleJson(readKey(adapter, 'schema'))
  const recorded: unknown = JSON.parse(readFileSync(path.join(directory, snapshot), 'utf8'))
  const before: unknown =
    kit.upSnapshot !== undefined && versionOf(recorded) < versionOf(current) ? kit.upSnapshot(recorded) : recorded
  return statementsOf(await kit.generateMigration(before, current))
}

/**
 * What a started adapter's migration directory records against the schema it builds.
 * @param adapter the database adapter of a started Payload instance.
 * @param root the member's directory, which the reported directory is relative to.
 * @returns the report.
 */
export const migrationDriftOf = async (adapter: unknown, root: string): Promise<MigrationDriftReport> => {
  if (typeof readKey(adapter, 'requireDrizzleKit') !== 'function') {
    return { kind: 'unsupported', adapter: String(readKey(adapter, 'packageName') ?? readKey(adapter, 'name')) }
  }
  const absolute: string = String(readKey(adapter, 'migrationDir'))
  const directory: string = path.relative(root, absolute) || '.'
  const snapshot: string | undefined = latestSnapshotIn(existsSync(absolute) ? readdirSync(absolute) : [])
  return snapshot === undefined
    ? { kind: 'unsnapshotted', directory }
    : { kind: 'compared', directory, snapshot, statements: await compare(adapter, absolute, snapshot) }
}

/**
 * Start the project's Payload without a database and report whether its migrations record its schema.
 * @param configFile the absolute path of the configuration.
 * @param payloadFile the absolute path of the entry of the `payload` package the project installed.
 * @returns the report line the gate parses, marker included.
 */
export const migrationDriftProbe = async (configFile: string, payloadFile: string): Promise<string> => {
  const config: unknown = await readKey(await import(pathToFileURL(configFile).href), 'default')
  const payload: unknown = readKey(await import(pathToFileURL(payloadFile).href), 'default')
  if (!isStartable(payload)) {
    throw new TypeError(`${payloadFile} exports no Payload instance to start`)
  }
  // The options `payload migrate:create` starts with: the schema is built, and nothing connects or runs.
  await payload.init({ config, disableDBConnect: true, disableOnInit: true })
  const report: MigrationDriftReport = await migrationDriftOf(readKey(payload, 'db'), process.cwd())
  return `${MIGRATION_DRIFT_REPORT_MARKER}${JSON.stringify(report)}`
}
