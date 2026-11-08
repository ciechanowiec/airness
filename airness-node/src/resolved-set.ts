// The resolved set of a node project, as the lockfile names it, matched to what pnpm installed on this
// host. The lockfile decides which packages exist; the installed copies only supply their manifests.
import type { InstalledPackage } from './installed-packages.js'
import { type Lockfile, type ResolvedPackage, splitPackageKey } from './lockfile.js'

/** The lockfile's packages, split by what this host holds of them. */
export interface ResolvedSet {
  /** The lockfile the set was read from. */
  readonly lockfile: Lockfile
  /** Each resolved package installed here, with its manifest. */
  readonly installed: readonly InstalledPackage[]
  /**
   * Resolved packages this host leaves out: each one limited to other systems, and what only such a package
   * depends on. This host cannot read them.
   */
  readonly elsewhere: readonly ResolvedPackage[]
  /** Resolved packages this host should hold and does not, which the install should have placed. */
  readonly missing: readonly ResolvedPackage[]
}

const coordinate = (name: string, version: string): string => `${name}@${version}`

// Every package this host should hold: what the importers reach through the snapshot edges, stopping at a
// package limited to other systems that the install left out, since what only it needs is left out with it.
// The walk advances one dependency level per step, so its depth is the depth of the graph, not the number of
// edges: the lockfile of a large monorepo holds thousands of edges and only tens of levels.
const expectedHere = (lockfile: Lockfile, isInstalled: (key: string) => boolean): ReadonlySet<string> => {
  const limited: ReadonlySet<string> = new Set(
    lockfile.packages
      .filter((resolved: ResolvedPackage): boolean => resolved.isPlatformLimited)
      .map((resolved: ResolvedPackage): string => coordinate(resolved.name, resolved.version)),
  )
  const isReachable = (key: string): boolean => {
    const { name, version } = splitPackageKey(key)
    const reached: string = coordinate(name, version)
    return !limited.has(reached) || isInstalled(reached)
  }
  const visit = (level: readonly string[], seen: ReadonlySet<string>): ReadonlySet<string> => {
    const fresh: readonly string[] = [...new Set(level)].filter(
      (key: string): boolean => !seen.has(key) && isReachable(key),
    )
    if (fresh.length === 0) {
      return seen
    }
    return visit(
      fresh.flatMap((key: string): readonly string[] => lockfile.edges.get(key) ?? []),
      new Set([...seen, ...fresh]),
    )
  }
  return new Set(
    [...visit(lockfile.roots, new Set())].map((key: string): string => {
      const { name, version } = splitPackageKey(key)
      return coordinate(name, version)
    }),
  )
}

/**
 * Match the lockfile's packages to the installed ones.
 * @param lockfile the project's lockfile.
 * @param installed the packages the virtual store holds.
 * @returns the resolved set.
 */
export const resolvedSetOf = (lockfile: Lockfile, installed: readonly InstalledPackage[]): ResolvedSet => {
  const byCoordinate: ReadonlyMap<string, InstalledPackage> = new Map(
    installed.map((entry: InstalledPackage): readonly [string, InstalledPackage] => [
      coordinate(entry.name, entry.version),
      entry,
    ]),
  )
  const expected: ReadonlySet<string> = expectedHere(lockfile, (key: string): boolean => byCoordinate.has(key))
  const absent: readonly ResolvedPackage[] = lockfile.packages.filter(
    (resolved: ResolvedPackage): boolean => !byCoordinate.has(coordinate(resolved.name, resolved.version)),
  )
  const isExpected = (resolved: ResolvedPackage): boolean => expected.has(coordinate(resolved.name, resolved.version))
  return {
    lockfile,
    installed: lockfile.packages.flatMap((resolved: ResolvedPackage): readonly InstalledPackage[] => {
      const entry: InstalledPackage | undefined = byCoordinate.get(coordinate(resolved.name, resolved.version))
      return entry === undefined ? [] : [entry]
    }),
    elsewhere: absent.filter((resolved: ResolvedPackage): boolean => !isExpected(resolved)),
    missing: absent.filter((resolved: ResolvedPackage): boolean => isExpected(resolved)),
  }
}

/**
 * The finding for each package the install should have placed and did not.
 * @param missing the packages.
 * @returns one finding per package.
 */
export const missingFindings = (missing: readonly ResolvedPackage[]): readonly string[] =>
  missing.map(
    (resolved: ResolvedPackage): string =>
      `${resolved.name}@${resolved.version} is resolved for this system but not installed; ` +
      'airness:node-dependencies installs the locked dependencies before the gates',
  )
