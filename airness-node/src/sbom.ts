// The npm software bill of materials of a node project, in CycloneDX 1.6: every package the lockfile
// resolves, with its package URL and declared license, and the dependency graph the lockfile records. It is
// written from the same resolved set as the inventory, offline and deterministically, so the same lockfile
// and installation always give the same document. Its package URLs are what an advisory scan matches.
import { createHash } from 'node:crypto'
import { type Inventory, inventoryOf } from './inventory.js'
import { type Lockfile, splitPackageKey } from './lockfile.js'
import type { ResolvedSet } from './resolved-set.js'

/** The evidence file the SBOM gate writes beside its result. */
export const SBOM_FILE: string = 'bom.cdx.json'

const SPEC_VERSION: string = '1.6'

// An SPDX expression: identifiers, `+`, parentheses and the three operators. Anything else, such as
// `SEE LICENSE IN LICENSE.md`, is recorded as a license name rather than as an expression it is not.
const SPDX_EXPRESSION: RegExp = /^[\w.+\-() ]+$/u
const NON_SPDX_WORDS: RegExp = /\b(?:SEE|LICENSE IN|UNLICENSED)\b/u

/** What the SBOM names the project and the harness that wrote it by. */
export interface SbomSubject {
  readonly name: string
  readonly version: string
  readonly harnessVersion: string
}

/**
 * The package URL of an npm package, with a scope written as its encoded namespace.
 * @param name the package name, scoped or not.
 * @param version the version.
 * @returns the package URL.
 */
const npmPackageUrl = (name: string, version: string): string => {
  const encoded: string = name.startsWith('@') ? `%40${name.slice(1)}` : name
  // A package URL without a version is valid; one ending in `@` is not, and a private application often
  // declares no version.
  return version.length > 0 ? `pkg:npm/${encoded}@${version}` : `pkg:npm/${encoded}`
}

const licensesOf = (license: string): readonly Readonly<Record<string, unknown>>[] => {
  if (license.length === 0) {
    return []
  }
  const isExpression: boolean = SPDX_EXPRESSION.test(license) && !NON_SPDX_WORDS.test(license)
  return [isExpression ? { expression: license } : { license: { name: license } }]
}

const nameParts = (name: string): Readonly<Record<string, string>> => {
  const slash: number = name.indexOf('/')
  return name.startsWith('@') && slash > 0 ? { group: name.slice(0, slash), name: name.slice(slash + 1) } : { name }
}

const referenceOf = (key: string): string => {
  const { name, version } = splitPackageKey(key)
  return npmPackageUrl(name, version)
}

const byText = (left: string, right: string): number => left.localeCompare(right, 'en')

// A link to another importer is a workspace member, which is no component of the bill, so no edge names it.
const isLink = (key: string): boolean => splitPackageKey(key).version.startsWith('link:')

const referencesOf = (keys: readonly string[]): readonly string[] =>
  [
    ...new Set(keys.filter((key: string): boolean => !isLink(key)).map((key: string): string => referenceOf(key))),
  ].toSorted(byText)

// Two snapshots of one package that differ only in the peers they were resolved with are one component, so
// its dependencies are the union of theirs.
const dependencyGraph = (lockfile: Lockfile, rootReference: string): readonly Readonly<Record<string, unknown>>[] => {
  const keys: readonly string[] = [...lockfile.edges.keys()]
  const edges: ReadonlyMap<string, readonly string[]> = new Map(
    [...new Set(keys.map((key: string): string => referenceOf(key)))].map((reference: string) => [
      reference,
      referencesOf(
        keys
          .filter((key: string): boolean => referenceOf(key) === reference)
          .flatMap((key: string): readonly string[] => lockfile.edges.get(key) ?? []),
      ),
    ]),
  )
  return [
    { ref: rootReference, dependsOn: referencesOf(lockfile.roots) },
    ...[...edges]
      .toSorted(([left]: readonly [string, unknown], [right]: readonly [string, unknown]): number =>
        byText(left, right),
      )
      .map(([reference, dependsOn]: readonly [string, readonly string[]]) => ({ ref: reference, dependsOn })),
  ]
}

// A serial number derived from the content rather than drawn at random, so an unchanged project writes the
// same document: the CycloneDX format requires an RFC 4122 URN, and this one is laid out as a version 5 UUID.
// The digest laid out as a UUID: five groups, the version digit and the variant digit written over the digest.
// The variant digit keeps the low two bits of the digest's and sets the top two to binary 10, as RFC 4122 asks.
const UUID_LAYOUT: RegExp =
  /^(?<first>[\da-f]{8})(?<second>[\da-f]{4})[\da-f](?<third>[\da-f]{3})(?<variant>[\da-f])(?<fourth>[\da-f]{3})(?<fifth>[\da-f]{12})/u
const HEX: number = 16
const VERSION_DIGIT: string = '5'
const VARIANT_BASE: number = 8
const VARIANT_SPAN: number = 4

const serialNumberOf = (text: string): string => {
  const groups: Readonly<Record<string, string>> =
    UUID_LAYOUT.exec(createHash('sha256').update(text).digest('hex'))?.groups ?? {}
  const part = (name: string): string => groups[name] ?? '0'
  const variant: string = ((Number.parseInt(part('variant'), HEX) % VARIANT_SPAN) + VARIANT_BASE).toString(HEX)
  return [
    `urn:uuid:${part('first')}`,
    part('second'),
    `${VERSION_DIGIT}${part('third')}`,
    `${variant}${part('fourth')}`,
    part('fifth'),
  ].join('-')
}

/**
 * The CycloneDX document of a node project's resolved set.
 * @param set the project's resolved set.
 * @param subject the project's name and version, and the harness version that writes it.
 * @returns the document.
 */
export const sbomOf = (set: ResolvedSet, subject: SbomSubject): Readonly<Record<string, unknown>> => {
  const inventory: Inventory = inventoryOf(set)
  const rootReference: string = npmPackageUrl(subject.name, subject.version)
  const components: readonly Readonly<Record<string, unknown>>[] = inventory.packages
    .toSorted((left, right): number =>
      npmPackageUrl(left.name, left.version).localeCompare(npmPackageUrl(right.name, right.version), 'en'),
    )
    .map((entry) => ({
      type: 'library',
      'bom-ref': npmPackageUrl(entry.name, entry.version),
      ...nameParts(entry.name),
      version: entry.version,
      purl: npmPackageUrl(entry.name, entry.version),
      licenses: licensesOf(entry.license),
      scope: entry.installed ? 'required' : 'optional',
      properties: [
        { name: 'airness:direct', value: String(entry.direct) },
        { name: 'airness:installed', value: String(entry.installed) },
      ],
    }))
  const body: Readonly<Record<string, unknown>> = {
    metadata: {
      tools: { components: [{ type: 'application', name: '@airness/node', version: subject.harnessVersion }] },
      component: {
        type: 'application',
        'bom-ref': rootReference,
        ...nameParts(subject.name),
        version: subject.version,
      },
    },
    components,
    dependencies: dependencyGraph(set.lockfile, rootReference),
  }
  return {
    bomFormat: 'CycloneDX',
    specVersion: SPEC_VERSION,
    serialNumber: serialNumberOf(JSON.stringify(body)),
    version: 1,
    ...body,
  }
}
