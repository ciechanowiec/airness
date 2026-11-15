import { existsSync, readFileSync, realpathSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { SECURITY_OVERRIDES, type SecurityOverride } from '@/security-overrides.js'

interface Manifest {
  readonly name: string
  readonly version: string
  readonly dependencies?: Readonly<Record<string, string>>
  readonly optionalDependencies?: Readonly<Record<string, string>>
}

interface Installed {
  readonly key: string
  readonly modules: string
  readonly manifest: Manifest
}

const PACKAGE_ROOT: string = fileURLToPath(new URL('..', import.meta.url))
const EXACT_VERSION: RegExp = /^\d+\.\d+\.\d+$/u

const read = (directory: string): Manifest =>
  JSON.parse(readFileSync(path.join(directory, 'package.json'), 'utf8')) as Manifest

const manifest: Manifest = read(PACKAGE_ROOT)

// pnpm links a package's own dependencies beside it, into the node_modules directory that holds the package, so
// that directory is where the package's requests resolve: one level up for a plain name, two for a scoped one.
const installed = (directory: string): Installed => {
  const found: Manifest = read(directory)
  const modules: string = found.name.split('/').reduce((location: string): string => path.dirname(location), directory)
  return { key: `${found.name}@${found.version}`, modules, manifest: found }
}

// Only a request for one exact version is followed. An optional dependency built for another platform is absent
// from the store, and a peer is the requester's to provide, so neither leads anywhere a consumer is held to.
const exactlyRequested = (modules: string, requester: Manifest): readonly Installed[] =>
  Object.entries({ ...requester.optionalDependencies, ...requester.dependencies })
    .filter(([, specifier]: readonly [string, string]): boolean => EXACT_VERSION.test(specifier))
    .map(([name]: readonly [string, string]): string => path.join(modules, name))
    .filter((location: string): boolean => existsSync(location))
    .map((location: string): Installed => installed(realpathSync(location)))

const reach = (frontier: readonly Installed[], seen: ReadonlySet<string>): ReadonlySet<string> => {
  const fresh: readonly Installed[] = frontier.filter(
    (entry: Installed, index: number): boolean =>
      !seen.has(entry.key) && frontier.findIndex((other: Installed): boolean => other.key === entry.key) === index,
  )
  return fresh.length === 0
    ? seen
    : reach(
        fresh.flatMap((entry: Installed): readonly Installed[] => exactlyRequested(entry.modules, entry.manifest)),
        new Set([...seen, ...fresh.map((entry: Installed): string => entry.key)]),
      )
}

// Every package a consumer installs at one version only, whatever the registry publishes later: those this package
// pins, and those they pin in turn, down every chain of exact requests.
const pinned: ReadonlySet<string> = reach(
  exactlyRequested(path.join(PACKAGE_ROOT, 'node_modules'), manifest),
  new Set(),
)

describe('the analyzers the package pins', () => {
  // type-coverage asks for its core by a caret range, so a fresh install would otherwise resolve the newest core
  // past the release-age floor rather than the one this package was built and approved with, and the security
  // replacement approved for that core's edge would then reach no resolved parent.
  it('pins the type-coverage core at the version of the command that runs it', () => {
    expect(manifest.dependencies?.['type-coverage-core']).toBe(manifest.dependencies?.['type-coverage'])
  })

  // An approval names its parent at one exact version, and the wiring gate refuses an approval that reaches no
  // resolved parent. A parent a consumer reaches through a range moves to each new release on its own, a day after
  // it is published, and the approval then fails every fresh install of a harness that has already shipped.
  it('reaches the parent of every approved replacement through exact versions alone', () => {
    expect(
      SECURITY_OVERRIDES.map((entry: SecurityOverride): string => entry.parent).filter(
        (parent: string): boolean => !pinned.has(parent),
      ),
    ).toEqual([])
  })

  it('does not count a package that is only requested by a range as pinned', () => {
    expect([...pinned].filter((key: string): boolean => key.startsWith('@eslint/config-array@'))).toEqual([])
  })
})
