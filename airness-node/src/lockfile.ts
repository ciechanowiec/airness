// What a node project's pnpm-lock.yaml resolves: each importer's direct dependencies, and every package
// of the resolved set. The gates that compare versions read the lockfile rather than package.json,
// because a specifier is a request and the lockfile is what pnpm installs.
import { parse, parseAllDocuments } from 'yaml'
import { asRecord, asText, isRecord } from './json-shapes.js'

/** The lockfile's name, at the root of the node project. */
export const LOCKFILE: string = 'pnpm-lock.yaml'

const DEPENDENCY_BLOCKS: readonly string[] = ['dependencies', 'devDependencies', 'optionalDependencies']

/** One direct dependency of an importer. */
export interface DirectDependency {
  readonly name: string
  /** What the importer's package.json asked for. */
  readonly specifier: string
  /** What pnpm resolved, without the peer suffix: a version, or a protocol such as `link:`. */
  readonly version: string
}

/** Where pnpm fetched a package from, as its resolution records it. */
type PackageSource = 'registry' | 'tarball' | 'git' | 'directory' | 'unknown'

/** One package of the resolved set. */
export interface ResolvedPackage {
  readonly name: string
  readonly version: string
  /**
   * `registry` for a package fetched from a registry by name, version and integrity; `tarball` for one whose
   * resolution names a URL, which pnpm also writes for a registry other than the default one.
   */
  readonly source: PackageSource
  /** The tarball URL the resolution names, empty when it names none. */
  readonly tarball: string
  /** Whether the package declares the systems it installs on, so a host outside them leaves it out. */
  readonly isPlatformLimited: boolean
}

/** The lockfile, read. */
export interface Lockfile {
  /** Each importer's direct dependencies, keyed by its path relative to the lockfile, `.` for the root. */
  readonly importers: ReadonlyMap<string, readonly DirectDependency[]>
  readonly packages: readonly ResolvedPackage[]
  /** The snapshot keys the importers depend on directly, such as `vitest@5.0.5(@types/node@26.6.5)`. */
  readonly roots: readonly string[]
  /** Each snapshot key with the snapshot keys it depends on, optional dependencies included. */
  readonly edges: ReadonlyMap<string, readonly string[]>
  /** Security replacements are judged against the harness catalogue, with their selected graph edges. */
  readonly overrides?: unknown
  /**
   * What the lockfile records of an install-time rewrite: an override, a patch, a package extension or a
   * pnpmfile hook. Each one changes what a resolved name and version contain.
   */
  readonly rewrites: readonly string[]
}

// The top-level sections pnpm writes when an install-time rewrite took part in the resolution.
const REWRITE_SECTIONS: readonly string[] = ['patchedDependencies', 'packageExtensionsChecksum', 'pnpmfileChecksum']

// A snapshot key carries the hash of the patch applied to it, `name@1.0.0(patch_hash=...)`.
const PATCH_MARKER: string = 'patch_hash='

const PLATFORM_FIELDS: readonly string[] = ['os', 'cpu', 'libc']

// A resolved version carries the peers it was resolved with, `5.0.5(@types/node@26.6.5)`.
const withoutPeers = (version: string): string => {
  const peers: number = version.indexOf('(')
  return peers === -1 ? version : version.slice(0, peers)
}

/**
 * Split a `name@version` key on its last `@`, since a scoped name starts with one.
 * @param key the key, such as `@airness/node@1.0.8`.
 * @returns the name and the version; the version is empty when the key has none.
 */
export const splitPackageKey = (key: string): { readonly name: string; readonly version: string } => {
  const plain: string = withoutPeers(key)
  const at: number = plain.lastIndexOf('@')
  return at > 0 ? { name: plain.slice(0, at), version: plain.slice(at + 1) } : { name: plain, version: '' }
}

const directDependencies = (importer: unknown): readonly DirectDependency[] =>
  DEPENDENCY_BLOCKS.flatMap((block: string): readonly DirectDependency[] =>
    Object.entries(asRecord(asRecord(importer)[block])).map(
      ([name, entry]: readonly [string, unknown]): DirectDependency => ({
        name,
        specifier: asText(asRecord(entry)['specifier']),
        version: withoutPeers(asText(asRecord(entry)['version'])),
      }),
    ),
  )

const sourceOf = (resolution: Record<string, unknown>): PackageSource => {
  if (resolution['type'] === 'git' || typeof resolution['repo'] === 'string') {
    return 'git'
  }
  if (typeof resolution['directory'] === 'string') {
    return 'directory'
  }
  // A resolution of a type this reading does not know, such as a future runtime or binary resolution, is no
  // registry release: classified as its own source, it fails every rule that requires a registry.
  if (resolution['type'] !== undefined) {
    return 'unknown'
  }
  return typeof resolution['tarball'] === 'string' ? 'tarball' : 'registry'
}

const resolvedPackage = (key: string, entry: unknown): ResolvedPackage => {
  const resolution: Record<string, unknown> = asRecord(asRecord(entry)['resolution'])
  return {
    ...splitPackageKey(key),
    source: sourceOf(resolution),
    tarball: asText(resolution['tarball']),
    isPlatformLimited: PLATFORM_FIELDS.some((field: string): boolean => asRecord(entry)[field] !== undefined),
  }
}

const EDGE_BLOCKS: readonly string[] = ['dependencies', 'optionalDependencies']

const edgesOf = (snapshot: unknown): readonly string[] =>
  EDGE_BLOCKS.flatMap((block: string): readonly string[] =>
    Object.entries(asRecord(asRecord(snapshot)[block])).map(
      ([name, version]: readonly [string, unknown]): string => `${name}@${asText(version)}`,
    ),
  )

// An importer's dependency on another importer is a link, which no snapshot describes.
const rootsOf = (importers: Record<string, unknown>): readonly string[] =>
  Object.values(importers).flatMap((importer: unknown): readonly string[] =>
    DEPENDENCY_BLOCKS.flatMap((block: string): readonly string[] =>
      Object.entries(asRecord(asRecord(importer)[block]))
        .map(([name, entry]: readonly [string, unknown]): string => `${name}@${asText(asRecord(entry)['version'])}`)
        .filter((key: string): boolean => !splitPackageKey(key).version.startsWith('link:')),
    ),
  )

const rewritesIn = (document: Record<string, unknown>): readonly string[] => [
  ...REWRITE_SECTIONS.filter((section: string): boolean => document[section] !== undefined).map(
    (section: string): string => `${LOCKFILE} records ${section}`,
  ),
  ...Object.keys(asRecord(document['snapshots']))
    .filter((key: string): boolean => key.includes(PATCH_MARKER))
    .map((key: string): string => `${LOCKFILE} resolves the patched ${splitPackageKey(key).name}`),
]

/**
 * Read a lockfile's text.
 * @param text the contents of pnpm-lock.yaml.
 * @returns the importers and the resolved set, or undefined when the text is no lockfile.
 */
export const readLockfile = (text: string): Lockfile | undefined => {
  const document: unknown = ((): unknown => {
    try {
      return parse(text)
    } catch {
      return undefined
    }
  })()
  if (!(isRecord(document) && isRecord(document['importers']))) {
    return undefined
  }
  return {
    importers: new Map(
      Object.entries(document['importers']).map(
        ([importer, entry]: readonly [string, unknown]): readonly [string, readonly DirectDependency[]] => [
          importer,
          directDependencies(entry),
        ],
      ),
    ),
    packages: Object.entries(asRecord(document['packages'])).map(
      ([key, entry]: readonly [string, unknown]): ResolvedPackage => resolvedPackage(key, entry),
    ),
    roots: rootsOf(document['importers']),
    edges: new Map(
      Object.entries(asRecord(document['snapshots'])).map(
        ([key, snapshot]: readonly [string, unknown]): readonly [string, readonly string[]] => [key, edgesOf(snapshot)],
      ),
    ),
    rewrites: rewritesIn(document),
    overrides: document['overrides'],
  }
}

/**
 * Why a lockfile's text yields no lockfile, for the finding of a gate that needs one.
 * @param text the contents of pnpm-lock.yaml, or undefined when the project has none.
 * @returns the cause, naming the lockfile.
 */
export const lockfileDefect = (text: string | undefined): string => {
  if (text === undefined) {
    return `${LOCKFILE} is missing; run pnpm install to write it`
  }
  // Unlike parse, parseAllDocuments records a syntax error in the document it occurs in rather than throwing.
  const documents: number = parseAllDocuments(text).length
  return documents > 1
    ? `${LOCKFILE} holds ${String(documents)} YAML documents: pnpm writes a document before the lockfile for ` +
        'configDependencies or a package manager it installs, which the harness refuses; remove them and run pnpm install'
    : `${LOCKFILE} is unreadable: it is no lockfile pnpm wrote; run pnpm install to write it again`
}
