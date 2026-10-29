// Proves that the package packs reproducibly. The build packs the tarball it attaches, rebuilds the package
// from an emptied dist and packs it again elsewhere; this compares the two tarballs byte for byte. Every
// consumer lockfile records the integrity of the tarball it installed, so a pack that differs from its own
// rebuild invalidates those locks although nothing shipped has changed. A difference is reported by entry,
// with what differs in it, rather than as two hashes. The tarball is also refused when it carries a build
// by-product or lacks a file its manifest exports, since the allowlist in `files` decides both.
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { gunzipSync } from 'node:zlib'

// The ustar layout: 512-byte blocks, and the header fields this comparison reads.
const BLOCK: number = 512
const NAME_END: number = 100
const SIZE_START: number = 124
const SIZE_END: number = 136
const PREFIX_START: number = 345
const PREFIX_END: number = 500
const OCTAL: number = 8
const ARGUMENT_OFFSET: number = 2

// The files a build writes beside its outputs that no consumer reads, such as TypeScript's incremental cache.
const BY_PRODUCTS: readonly RegExp[] = [/\.tsbuildinfo$/u]

// The manifest keys whose targets a consumer resolves inside the installed package.
const SHIPPED_KEYS: ReadonlySet<string> = new Set<string>(['bin', 'exports'])

const MANIFEST_ENTRY: string = 'package/package.json'

/** One entry of a tar archive: its path, its header block and its content. */
export interface TarEntry {
  readonly name: string
  readonly header: Buffer
  readonly content: Buffer
}

const field = (header: Buffer, start: number, end: number): string => {
  const bytes: Buffer = header.subarray(start, end)
  const terminator: number = bytes.indexOf(0)
  return bytes.subarray(0, terminator === -1 ? bytes.length : terminator).toString('utf8')
}

const entryName = (header: Buffer): string => {
  const prefix: string = field(header, PREFIX_START, PREFIX_END)
  const name: string = field(header, 0, NAME_END)
  return prefix === '' ? name : `${prefix}/${name}`
}

const entrySize = (header: Buffer, offset: number): number => {
  const size: number = Number.parseInt(field(header, SIZE_START, SIZE_END).trim(), OCTAL)
  if (!Number.isSafeInteger(size)) {
    throw new TypeError(`the tar header at byte ${String(offset)} has no readable size`)
  }
  return size
}

// Where a walk over the blocks of an archive stands: the offset of the next header, the entries read so far,
// and whether the end-of-archive block has been reached.
interface Walk {
  readonly next: number
  readonly entries: readonly TarEntry[]
  readonly isEnded: boolean
}

const START: Walk = { next: 0, entries: [], isEnded: false }

// One block of the walk. Only the block where the next header lies is read; content blocks pass unchanged.
const walkBlock = (archive: Buffer, walk: Walk, block: number): Walk => {
  const offset: number = block * BLOCK
  if (walk.isEnded || offset !== walk.next) {
    return walk
  }
  const header: Buffer = archive.subarray(offset, offset + BLOCK)
  if (header.length < BLOCK) {
    throw new TypeError(`the archive ends inside the tar header at byte ${String(offset)}`)
  }
  if (header.every((byte: number): boolean => byte === 0)) {
    return { ...walk, isEnded: true }
  }
  const size: number = entrySize(header, offset)
  const start: number = offset + BLOCK
  const content: Buffer = archive.subarray(start, start + size)
  if (content.length < size) {
    throw new TypeError(`the archive ends inside the content of ${entryName(header)}`)
  }
  return {
    next: start + Math.ceil(size / BLOCK) * BLOCK,
    entries: walk.entries.concat([{ name: entryName(header), header, content }]),
    isEnded: false,
  }
}

/**
 * Read the entries of a gzipped tar archive, such as the one `pnpm pack` writes. The archive is walked as a
 * fold over its block indices, so its stack depth does not grow with the number of entries.
 * @param gzipped the archive as packed.
 * @returns its entries in archive order.
 */
export const tarEntries = (gzipped: Buffer): readonly TarEntry[] => {
  const archive: Buffer = gunzipSync(gzipped)
  const walk: Walk = Array.from(
    { length: Math.ceil(archive.length / BLOCK) },
    (_unused: unknown, block: number): number => block,
  ).reduce((reached: Walk, block: number): Walk => walkBlock(archive, reached, block), START)
  if (!walk.isEnded) {
    throw new TypeError(`the archive ends inside the tar header at byte ${String(walk.next)}`)
  }
  return walk.entries
}

const entryDifference = (name: string, first: TarEntry | undefined, second: TarEntry | undefined): string[] => {
  if (first === undefined) {
    return [`${name}: only in the second pack`]
  }
  if (second === undefined) {
    return [`${name}: only in the first pack`]
  }
  if (!first.content.equals(second.content)) {
    return [`${name}: the content differs (${String(first.content.length)} and ${String(second.content.length)} bytes)`]
  }
  return first.header.equals(second.header) ? [] : [`${name}: the header differs (mode, owner or time)`]
}

const byName = (entries: readonly TarEntry[]): ReadonlyMap<string, TarEntry> =>
  new Map<string, TarEntry>(entries.map((entry: TarEntry): readonly [string, TarEntry] => [entry.name, entry]))

/**
 * Name every entry in which two packs differ.
 * @param first the first pack.
 * @param second the second pack.
 * @returns one line per differing entry, or what else differs when every entry is equal; none for equal bytes.
 */
export const packDifferences = (first: Buffer, second: Buffer): readonly string[] => {
  if (first.equals(second)) {
    return []
  }
  const firstEntries: ReadonlyMap<string, TarEntry> = byName(tarEntries(first))
  const secondEntries: ReadonlyMap<string, TarEntry> = byName(tarEntries(second))
  const names: readonly string[] = [...new Set<string>([...firstEntries.keys(), ...secondEntries.keys()])].toSorted(
    (left: string, right: string): number => left.localeCompare(right),
  )
  const differences: readonly string[] = names.flatMap((name: string): string[] =>
    entryDifference(name, firstEntries.get(name), secondEntries.get(name)),
  )
  if (differences.length > 0) {
    return differences
  }
  return [
    gunzipSync(first).equals(gunzipSync(second))
      ? 'the gzip streams differ although the archives inside them are identical'
      : 'the archives hold equal entries in a different order or layout',
  ]
}

const targetsOf = (value: unknown): readonly string[] => {
  if (typeof value === 'string') {
    return [value]
  }
  return typeof value === 'object' && value !== null
    ? Object.values(value).flatMap((target: unknown): readonly string[] => targetsOf(target))
    : []
}

const shippedTargets = (manifest: unknown): readonly string[] =>
  typeof manifest === 'object' && manifest !== null
    ? Object.entries(manifest)
        .filter(([key]: readonly [string, unknown]): boolean => SHIPPED_KEYS.has(key))
        .flatMap(([, value]: readonly [string, unknown]): readonly string[] => targetsOf(value))
    : []

/**
 * Judge what one pack ships: no build by-product, and every file its manifest exports or runs as a binary.
 * @param entries the entries of the pack.
 * @returns one line per finding; none for a conforming pack.
 */
export const packContentFindings = (entries: readonly TarEntry[]): readonly string[] => {
  const byProducts: readonly string[] = entries
    .filter((entry: TarEntry): boolean => BY_PRODUCTS.some((pattern: RegExp): boolean => pattern.test(entry.name)))
    .map((entry: TarEntry): string => `${entry.name}: a build by-product is packed; keep it out of files`)
  const manifest: TarEntry | undefined = entries.find((entry: TarEntry): boolean => entry.name === MANIFEST_ENTRY)
  if (manifest === undefined) {
    return [...byProducts, `${MANIFEST_ENTRY}: the pack holds no manifest`]
  }
  const packed: ReadonlySet<string> = new Set<string>(entries.map((entry: TarEntry): string => entry.name))
  const missing: readonly string[] = shippedTargets(JSON.parse(manifest.content.toString('utf8')))
    .map((target: string): string => path.posix.join('package', target))
    .filter((target: string): boolean => !packed.has(target))
    .map((target: string): string => `${target}: the manifest names it, but files does not pack it`)
  return [...byProducts, ...missing]
}

/**
 * The file name `pnpm pack` gives the tarball of a package: its name without the scope marker, then its version.
 * @param manifestText the package.json of the package.
 * @returns the tarball's file name.
 */
export const tarballName = (manifestText: string): string => {
  const manifest: unknown = JSON.parse(manifestText)
  const fields: ReadonlyMap<string, unknown> = new Map<string, unknown>(
    typeof manifest === 'object' && manifest !== null ? Object.entries(manifest) : [],
  )
  const read = (key: string): string => {
    const value: unknown = fields.get(key)
    if (typeof value !== 'string') {
      throw new TypeError(`package.json declares no ${key}`)
    }
    return value
  }
  return `${read('name').replace(/^@/u, '').replace('/', '-')}-${read('version')}.tgz`
}

/**
 * Compare the package's tarball in two pack directories and report the verdict on the console.
 * @param root the package directory, whose package.json names the tarball.
 * @param directories the directory of the first pack, the one shipped, and of the second.
 * @returns the exit code: 0 when the packs are byte-identical and the first one conforms, else 1.
 */
export const reportPacks = (root: string, directories: readonly string[]): number => {
  const [firstDirectory, secondDirectory, ...surplus]: readonly (string | undefined)[] = directories
  if (firstDirectory === undefined || secondDirectory === undefined || surplus.length > 0) {
    throw new TypeError('Expected the directories of the first and the second pack')
  }
  const name: string = tarballName(readFileSync(path.join(root, 'package.json'), 'utf8'))
  const first: Buffer = readFileSync(path.resolve(root, firstDirectory, name))
  const second: Buffer = readFileSync(path.resolve(root, secondDirectory, name))
  const differences: readonly string[] = packDifferences(first, second).map(
    (line: string): string => `${name} is not reproducible: ${line}`,
  )
  const findings: readonly string[] = [...differences, ...packContentFindings(tarEntries(first))]
  if (findings.length > 0) {
    console.error(findings.join('\n'))
    return 1
  }
  console.info(`${name} packs byte-identically from the same tree and ships no build by-product`)
  return 0
}

if (import.meta.main) {
  process.exitCode = reportPacks(process.cwd(), process.argv.slice(ARGUMENT_OFFSET))
}
