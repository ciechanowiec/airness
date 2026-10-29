// The npm packages the Payload layer refuses by name, the Payload input to the generic blocklist. The
// framework-neutral entries (MongoDB drivers, Elastic, Ghostscript wrappers and the rest) are the generic
// layer's; these exist because of Payload: its MongoDB adapter, and the drivers of database servers Payload has
// no adapter for, which a project could reach only to run a second, proprietary database beside its own.

import { asRecord, readKey } from '@airness/node/json-shapes'
import { parse } from 'yaml'

/** One refused package, with why it is refused and what to use instead. */
export interface BlockedPackage {
  readonly name: string
  readonly reason: string
  readonly replacement: string
}

const MONGODB_REASON: string =
  'the MongoDB server is SSPL, which is not an open-source licence, and this adapter exists only to reach it'
const PROPRIETARY_DATABASE_REASON: string = 'a proprietary database server, for which Payload has no adapter'

/** The packages the Payload layer refuses. */
const BLOCKED_PACKAGES: readonly BlockedPackage[] = [
  { name: '@payloadcms/db-mongodb', reason: MONGODB_REASON, replacement: '@payloadcms/db-postgres' },
  { name: 'mssql', reason: PROPRIETARY_DATABASE_REASON, replacement: 'pg, through @payloadcms/db-postgres' },
  { name: 'tedious', reason: PROPRIETARY_DATABASE_REASON, replacement: 'pg, through @payloadcms/db-postgres' },
  { name: 'oracledb', reason: PROPRIETARY_DATABASE_REASON, replacement: 'pg, through @payloadcms/db-postgres' },
  { name: 'ibm_db', reason: PROPRIETARY_DATABASE_REASON, replacement: 'pg, through @payloadcms/db-postgres' },
]

// The name in a lockfile key such as `@scope/name@1.2.3` or `name@1.2.3(peer@4.5.6)`.
const nameOfKey = (key: string): string => {
  const at: number = key.indexOf('@', 1)
  return at === -1 ? key : key.slice(0, at)
}

/**
 * The packages a pnpm lockfile resolves, whatever depth reaches them.
 * @param lockfile the text of `pnpm-lock.yaml`.
 * @returns each resolved package name once, sorted.
 */
export const resolvedPackagesIn = (lockfile: string): readonly string[] => {
  const packages: Record<string, unknown> = asRecord(readKey(parse(lockfile), 'packages'))
  const names: readonly string[] = Object.keys(packages).map((key: string): string => nameOfKey(key))
  return [...new Set<string>(names)].toSorted((left: string, right: string): number => left.localeCompare(right))
}

/**
 * Every refused package the lockfile resolves.
 * @param resolved the resolved package names.
 * @returns one finding per refused package, naming its reason and its replacement.
 */
export const findBlockedPackages = (resolved: readonly string[]): readonly string[] =>
  BLOCKED_PACKAGES.filter((blocked: BlockedPackage): boolean => resolved.includes(blocked.name)).map(
    (blocked: BlockedPackage): string =>
      `${blocked.name} is refused by the Payload layer: ${blocked.reason}. Use ${blocked.replacement} instead, ` +
      'and remove whatever depends on it',
  )
