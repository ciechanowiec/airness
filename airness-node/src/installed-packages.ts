// The packages pnpm installed for a node project, read from its virtual store: every resolved package that
// is on disk, with its own manifest. The store lives at the project root and holds every member's packages.
import { type Dirent, existsSync, lstatSync, readdirSync } from 'node:fs'
import path from 'node:path'
import { asRecord, asText } from './json-shapes.js'
import { readJson } from './project.js'

/** The directory pnpm keeps each resolved package in, below the project root. */
const VIRTUAL_STORE: string = path.join('node_modules', '.pnpm')

/** One installed package. */
export interface InstalledPackage {
  readonly name: string
  readonly version: string
  /** The installed package's directory. */
  readonly directory: string
  /** Its parsed package.json. */
  readonly manifest: unknown
}

const SCOPE_PREFIX: string = '@'

const directories = (parent: string): readonly Dirent[] =>
  readdirSync(parent, { withFileTypes: true }).filter((entry: Dirent): boolean => entry.isDirectory())

// A store entry's node_modules holds the package itself as a directory and its dependencies as links.
const packageDirectories = (modules: readonly string[]): readonly string[] =>
  modules.flatMap((directory: string): readonly string[] =>
    directories(directory).flatMap((entry: Dirent): readonly string[] =>
      entry.name.startsWith(SCOPE_PREFIX)
        ? directories(path.join(directory, entry.name)).map((scoped: Dirent): string =>
            path.join(directory, entry.name, scoped.name),
          )
        : [path.join(directory, entry.name)],
    ),
  )

const installedAt = (directory: string): InstalledPackage | undefined => {
  const manifest: unknown = readJson(path.join(directory, 'package.json'))
  const name: string = asText(asRecord(manifest)['name'])
  return name.length === 0 || lstatSync(directory).isSymbolicLink()
    ? undefined
    : { name, version: asText(asRecord(manifest)['version']), directory, manifest }
}

/**
 * Every package installed in a node project's virtual store, once per name and version.
 * @param projectRoot the node project directory.
 * @returns the installed packages sorted by name and version, or undefined when nothing is installed.
 */
export const installedPackages = (projectRoot: string): readonly InstalledPackage[] | undefined => {
  const store: string = path.join(projectRoot, VIRTUAL_STORE)
  if (!existsSync(store)) {
    return undefined
  }
  const modules: readonly string[] = directories(store)
    .filter((entry: Dirent): boolean => entry.name !== 'node_modules')
    .map((entry: Dirent): string => path.join(store, entry.name, 'node_modules'))
    .filter((directory: string): boolean => existsSync(directory))
  const found: ReadonlyMap<string, InstalledPackage> = new Map(
    packageDirectories(modules)
      .map((directory: string): InstalledPackage | undefined => installedAt(directory))
      .filter((installed: InstalledPackage | undefined): installed is InstalledPackage => installed !== undefined)
      .map((installed: InstalledPackage): readonly [string, InstalledPackage] => [
        `${installed.name}@${installed.version}`,
        installed,
      ]),
  )
  return [...found.values()].toSorted((left: InstalledPackage, right: InstalledPackage): number =>
    `${left.name}@${left.version}`.localeCompare(`${right.name}@${right.version}`, 'en'),
  )
}

const INSTALL_SCRIPTS: readonly string[] = ['preinstall', 'install', 'postinstall']

/**
 * Whether installing the package runs a script: a lifecycle script of its own, or the native build pnpm
 * runs for a package that ships a `binding.gyp` and names no install script.
 * @param installed the installed package.
 * @returns true when pnpm would run a script for it.
 */
export const hasInstallScript = (installed: InstalledPackage): boolean => {
  const scripts: Record<string, unknown> = asRecord(asRecord(installed.manifest)['scripts'])
  const hasDeclaredScript: boolean = INSTALL_SCRIPTS.some(
    (script: string): boolean => asText(scripts[script]).length > 0,
  )
  return hasDeclaredScript || existsSync(path.join(installed.directory, 'binding.gyp'))
}
