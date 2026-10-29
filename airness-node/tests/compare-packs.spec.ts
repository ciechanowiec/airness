// The reproducibility check of the package's own build, against tarballs written here in the ustar layout
// `pnpm pack` writes. A check that stopped reading entries would report two hashes rather than the file that
// differs, and one that stopped judging content would let a TypeScript build cache ship again.
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { gzipSync } from 'node:zlib'
import { afterAll, describe, expect, it } from 'vitest'
import {
  packContentFindings,
  packDifferences,
  reportPacks,
  type TarEntry,
  tarballName,
  tarEntries,
} from '#package/compare-packs'

const BLOCK: number = 512
const NAME_END: number = 100
const MODE_START: number = 100
const SIZE_START: number = 124
const TIME_START: number = 136
const TYPE_START: number = 156
const MAGIC_START: number = 257
const PREFIX_START: number = 345
const OCTAL: number = 8
const SIZE_DIGITS: number = 11
const MODE_DIGITS: number = 7
const FILE_MODE: number = 0o644
const OTHER_MODE: number = 0o755
const PACKED_TIME: number = 499_162_500
const OTHER_TIME: number = 499_162_501
const FAST: number = 1
const SMALL: number = 9

/** One file of a synthetic pack. */
interface PackedFile {
  readonly name: string
  readonly content: string
  readonly mode?: number
  readonly time?: number
  readonly prefix?: string
}

const header = (file: PackedFile, size: number): Buffer => {
  const block: Buffer = Buffer.alloc(BLOCK)
  block.write(file.name.slice(0, NAME_END), 0, 'utf8')
  block.write(`${(file.mode ?? FILE_MODE).toString(OCTAL).padStart(MODE_DIGITS, '0')}\0`, MODE_START, 'utf8')
  block.write(`${size.toString(OCTAL).padStart(SIZE_DIGITS, '0')}\0`, SIZE_START, 'utf8')
  block.write(`${(file.time ?? PACKED_TIME).toString(OCTAL).padStart(SIZE_DIGITS, '0')}\0`, TIME_START, 'utf8')
  block.write('0', TYPE_START, 'utf8')
  block.write('ustar\u{0}00', MAGIC_START, 'utf8')
  block.write(file.prefix ?? '', PREFIX_START, 'utf8')
  return block
}

const archive = (files: readonly PackedFile[]): Buffer =>
  Buffer.concat([
    ...files.flatMap((file: PackedFile): readonly Buffer[] => {
      const content: Buffer = Buffer.from(file.content, 'utf8')
      const padding: number = (BLOCK - (content.length % BLOCK)) % BLOCK
      return [header(file, content.length), content, Buffer.alloc(padding)]
    }),
    Buffer.alloc(BLOCK + BLOCK),
  ])

const pack = (files: readonly PackedFile[], level: number = SMALL): Buffer => gzipSync(archive(files), { level })

const MANIFEST: string = JSON.stringify({
  name: '@airness/node',
  version: '1.0.0',
  bin: { 'airness-node': './dist/bin.js' },
  exports: { '.': { types: './dist/index.d.ts', default: './dist/index.js' }, './biome': './configs/biome.json' },
})

const BINARY: PackedFile = { name: 'package/dist/bin.js', content: 'console.info(1)\n' }

const SHIPPED: readonly PackedFile[] = [
  BINARY,
  { name: 'package/dist/index.d.ts', content: 'export declare const one: number\n' },
  { name: 'package/dist/index.js', content: 'export const one = 1\n' },
  { name: 'package/configs/biome.json', content: '{}\n' },
  { name: 'package/package.json', content: MANIFEST },
]

const scratch: string = mkdtempSync(path.join(tmpdir(), 'airness-compare-packs-'))

afterAll(() => {
  rmSync(scratch, { recursive: true, force: true })
})

describe('tarEntries', () => {
  it('reads every entry with its full path and content, in archive order', () => {
    const entries: readonly TarEntry[] = tarEntries(
      pack([...SHIPPED, { name: 'long.js', prefix: 'package/dist/deep', content: 'x' }]),
    )
    expect(entries.map((entry: TarEntry): string => entry.name)).toEqual([
      ...SHIPPED.map((file: PackedFile): string => file.name),
      'package/dist/deep/long.js',
    ])
    expect(entries.at(-1)?.content.toString('utf8')).toBe('x')
  })

  it('refuses an archive that ends inside a header, inside a content or at an unreadable size', () => {
    const whole: Buffer = archive(SHIPPED)
    expect(() => tarEntries(gzipSync(whole.subarray(0, BLOCK + BLOCK + BLOCK / 2)))).toThrow('inside the tar header')
    const unterminated: Buffer = whole.subarray(0, whole.length - BLOCK - BLOCK)
    expect(() => tarEntries(gzipSync(unterminated))).toThrow(
      `inside the tar header at byte ${String(unterminated.length)}`,
    )
    const cut: Buffer = Buffer.concat([header(BINARY, BLOCK + BLOCK), Buffer.alloc(BLOCK)])
    expect(() => tarEntries(gzipSync(cut))).toThrow('inside the content of package/dist/bin.js')
    const unreadable: Buffer = Buffer.from(whole)
    unreadable.write('not-octal!!\0', SIZE_START, 'utf8')
    expect(() => tarEntries(gzipSync(unreadable))).toThrow('has no readable size')
  })
})

describe('packDifferences', () => {
  it('finds nothing in two packs of the same bytes', () => {
    expect(packDifferences(pack(SHIPPED), pack(SHIPPED))).toEqual([])
  })

  it('names each entry that only one pack holds, whose content differs, or whose header differs', () => {
    const changed: readonly PackedFile[] = [
      { ...BINARY, mode: OTHER_MODE },
      { name: 'package/dist/index.d.ts', content: 'export declare const one: number\n', time: OTHER_TIME },
      { name: 'package/dist/index.js', content: 'export const one = 2\n' },
      { name: 'package/package.json', content: MANIFEST },
      { name: 'package/dist/tsconfig.tsbuildinfo', content: '{}' },
    ]
    expect(packDifferences(pack(SHIPPED), pack(changed))).toEqual([
      'package/configs/biome.json: only in the first pack',
      'package/dist/bin.js: the header differs (mode, owner or time)',
      'package/dist/index.d.ts: the header differs (mode, owner or time)',
      'package/dist/index.js: the content differs (21 and 21 bytes)',
      'package/dist/tsconfig.tsbuildinfo: only in the second pack',
    ])
  })

  it('tells a difference in the gzip stream alone from one in the order of equal entries', () => {
    expect(packDifferences(pack(SHIPPED), pack(SHIPPED, FAST))).toEqual([
      'the gzip streams differ although the archives inside them are identical',
    ])
    expect(packDifferences(pack(SHIPPED), pack(SHIPPED.toReversed()))).toEqual([
      'the archives hold equal entries in a different order or layout',
    ])
  })
})

describe('packContentFindings', () => {
  it('accepts a pack that ships every target its manifest names and no by-product', () => {
    expect(packContentFindings(tarEntries(pack(SHIPPED)))).toEqual([])
  })

  it('refuses a packed TypeScript build cache and a manifest target the pack lacks', () => {
    const withCache: readonly PackedFile[] = [
      ...SHIPPED.filter((file: PackedFile): boolean => file.name !== 'package/dist/index.d.ts'),
      { name: 'package/dist/tsconfig.tsbuildinfo', content: '{}' },
    ]
    expect(packContentFindings(tarEntries(pack(withCache)))).toEqual([
      'package/dist/tsconfig.tsbuildinfo: a build by-product is packed; keep it out of files',
      'package/dist/index.d.ts: the manifest names it, but files does not pack it',
    ])
  })

  it('refuses a pack without a manifest', () => {
    const findings: readonly string[] = packContentFindings(tarEntries(pack(SHIPPED.slice(0, 1))))
    expect(findings).toEqual(['package/package.json: the pack holds no manifest'])
  })
})

describe('tarballName', () => {
  it('names the tarball as pnpm pack does, for a scoped and an unscoped package', () => {
    expect(tarballName(MANIFEST)).toBe('airness-node-1.0.0.tgz')
    expect(tarballName(JSON.stringify({ name: 'library', version: '2.0.1' }))).toBe('library-2.0.1.tgz')
  })

  it('refuses a manifest without a version', () => {
    expect(() => tarballName(JSON.stringify({ name: 'library' }))).toThrow('package.json declares no version')
    expect(() => tarballName('[]')).toThrow('package.json declares no name')
  })
})

// A package directory holding the manifest and the tarball of each of the two packs.
const project = (name: string, first: Buffer, second: Buffer): string => {
  const root: string = path.join(scratch, name)
  mkdirSync(path.join(root, 'target', 'repack'), { recursive: true })
  writeFileSync(path.join(root, 'package.json'), MANIFEST)
  writeFileSync(path.join(root, 'target', 'airness-node-1.0.0.tgz'), first)
  writeFileSync(path.join(root, 'target', 'repack', 'airness-node-1.0.0.tgz'), second)
  return root
}

describe('reportPacks', () => {
  it('passes two byte-identical packs of a conforming package', () => {
    expect(reportPacks(project('same', pack(SHIPPED), pack(SHIPPED)), ['target', 'target/repack'])).toBe(0)
  })

  it('fails two packs that differ and a pack that ships a by-product', () => {
    const cache: readonly PackedFile[] = [...SHIPPED, { name: 'package/dist/tsconfig.tsbuildinfo', content: '{}' }]
    expect(reportPacks(project('differing', pack(SHIPPED), pack(cache)), ['target', 'target/repack'])).toBe(1)
    expect(reportPacks(project('by-product', pack(cache), pack(cache)), ['target', 'target/repack'])).toBe(1)
  })

  it('refuses anything but the two pack directories', () => {
    const root: string = project('arguments', pack(SHIPPED), pack(SHIPPED))
    expect(() => reportPacks(root, ['target'])).toThrow('Expected the directories of the first and the second pack')
    expect(() => reportPacks(root, ['target', 'target/repack', 'target'])).toThrow('Expected the directories')
  })
})
