import { readdir } from 'node:fs/promises'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

// Every migration `payload migrate:create` writes comes with the schema snapshot the next one is diffed
// against. Whether the newest snapshot still matches the config is the `payload-migrations` gate's verdict.

const MIGRATIONS_DIRECTORY: string = path.resolve(process.cwd(), 'src/migrations')
const SNAPSHOT_SUFFIX: string = '.json'
const MIGRATION_SUFFIX: string = '.ts'
const BARREL: string = `index${MIGRATION_SUFFIX}`

const byName = (first: string, second: string): number => first.localeCompare(second)

/** The names in src/migrations carrying the given suffix, oldest first, excluding the barrel. */
const namesEndingWith = async (suffix: string): Promise<readonly string[]> => {
  const entries: readonly string[] = await readdir(MIGRATIONS_DIRECTORY)
  return entries
    .filter((entry: string): boolean => entry.endsWith(suffix) && entry !== BARREL)
    .map((entry: string): string => entry.slice(0, -suffix.length))
    .sort(byName)
}

describe('schema migrations', () => {
  it('carries a schema snapshot beside every migration', async () => {
    const migrations: readonly string[] = await namesEndingWith(MIGRATION_SUFFIX)
    const snapshots: readonly string[] = await namesEndingWith(SNAPSHOT_SUFFIX)

    expect(migrations.length).toBeGreaterThan(0)
    expect(snapshots).toEqual(migrations)
  })
})
