// A virtual store laid out the way pnpm lays one out, for the gates that read installed packages: each
// package's own directory below `node_modules/.pnpm/<entry>/node_modules`, beside links to its dependencies.
import { mkdirSync, symlinkSync, writeFileSync } from 'node:fs'
import path from 'node:path'

/** One package to install into the store. */
export interface StoredPackage {
  readonly name: string
  readonly version: string
  /** Further fields of its package.json. */
  readonly manifest?: Readonly<Record<string, unknown>>
  /** Further files of the package, by package-relative path. */
  readonly files?: Readonly<Record<string, string>>
}

const entryModules = (projectRoot: string, stored: StoredPackage): string =>
  path.join(projectRoot, 'node_modules', '.pnpm', `${stored.name.replace('/', '+')}@${stored.version}`, 'node_modules')

/**
 * Install packages into a project's virtual store, each beside a link to the package before it.
 * @param projectRoot the node project directory.
 * @param packages the packages.
 */
export const installIntoStore = (projectRoot: string, packages: readonly StoredPackage[]): void => {
  mkdirSync(path.join(projectRoot, 'node_modules', '.pnpm', 'node_modules'), { recursive: true })
  const directories: readonly string[] = packages.map((stored: StoredPackage): string => {
    const directory: string = path.join(entryModules(projectRoot, stored), stored.name)
    mkdirSync(directory, { recursive: true })
    writeFileSync(
      path.join(directory, 'package.json'),
      JSON.stringify({ name: stored.name, version: stored.version, ...stored.manifest }),
    )
    const files: readonly (readonly [string, string])[] = Object.entries(stored.files ?? {})
    for (const [file, content] of files) {
      writeFileSync(path.join(directory, file), content)
    }
    return directory
  })
  for (const [index, directory] of directories.slice(1).entries()) {
    symlinkSync(String(directories[index]), path.join(path.dirname(directory), `link-${String(index)}`))
  }
}
