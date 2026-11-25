// The npm dependency evidence the repository's dependency policies read: every package the node project's
// lockfile resolves, with its version, its declared license, whether a member depends on it directly, whether
// this host installed it, whether the product runs it, and the notice files each such package ships. It judges
// nothing; the policies and the compliance report that read it do.
import type { InstalledPackage } from './installed-packages.js'
import { asRecord, asText, isArray } from './json-shapes.js'
import { type DirectDependency, productionKeys, type ResolvedPackage } from './lockfile.js'
import { packageNotices } from './package-notices.js'
import type { ResolvedSet } from './resolved-set.js'

/** The evidence file the inventory writes beside its result. */
export const INVENTORY_FILE: string = 'dependencies.json'

/** The version of the evidence file's shape. */
const INVENTORY_CONTRACT: number = 2

/** One package of the inventory. */
interface InventoryEntry {
  readonly name: string
  readonly version: string
  /** The license expression the package declares, empty when it declares none. */
  readonly license: string
  /** Whether a member of the project declares it. */
  readonly direct: boolean
  /** Whether this host installed it; a package limited to other systems is resolved but not installed. */
  readonly installed: boolean
  /** Whether a member's `dependencies` or `optionalDependencies` reach it, so the product runs it. */
  readonly production: boolean
  /** The notice files an installed production package ships, keyed by file name; empty for any other. */
  readonly notices: Readonly<Record<string, string>>
}

/** The evidence file. */
export interface Inventory {
  readonly contract: number
  readonly packages: readonly InventoryEntry[]
}

// `license` is an SPDX expression, or an object in an old manifest; `licenses` is the older list form.
const licenseOf = (manifest: unknown): string => {
  const record: Record<string, unknown> = asRecord(manifest)
  const declared: unknown = record['license']
  if (typeof declared === 'string') {
    return declared
  }
  if (declared !== undefined) {
    return asText(asRecord(declared)['type'])
  }
  const licenses: unknown = record['licenses']
  return (isArray(licenses) ? licenses : [])
    .map((entry: unknown): string => asText(asRecord(entry)['type']))
    .filter((type: string): boolean => type.length > 0)
    .join(' OR ')
}

const isDirect = (direct: readonly DirectDependency[], name: string, version: string): boolean =>
  direct.some((dependency: DirectDependency): boolean => dependency.name === name && dependency.version === version)

/**
 * The inventory of a node project: every package the lockfile resolves, with the license of its installed
 * manifest and, for a production package, its notice files, and the packages limited to other systems listed
 * as not installed, without a license.
 * @param set the project's resolved set.
 * @returns the evidence.
 */
export const inventoryOf = (set: ResolvedSet): Inventory => {
  const direct: readonly DirectDependency[] = [...set.lockfile.importers.values()].flat()
  const production: ReadonlySet<string> = productionKeys(set.lockfile)
  const isProduction = (name: string, version: string): boolean => production.has(`${name}@${version}`)
  return {
    contract: INVENTORY_CONTRACT,
    packages: [
      ...set.installed.map(
        (entry: InstalledPackage): InventoryEntry => ({
          name: entry.name,
          version: entry.version,
          license: licenseOf(entry.manifest),
          direct: isDirect(direct, entry.name, entry.version),
          installed: true,
          production: isProduction(entry.name, entry.version),
          notices: isProduction(entry.name, entry.version) ? packageNotices(entry.directory) : {},
        }),
      ),
      ...set.elsewhere.map(
        (resolved: ResolvedPackage): InventoryEntry => ({
          name: resolved.name,
          version: resolved.version,
          license: '',
          direct: isDirect(direct, resolved.name, resolved.version),
          installed: false,
          production: isProduction(resolved.name, resolved.version),
          notices: {},
        }),
      ),
    ],
  }
}
