// Whether a node project installs the harness the way its Maven parents decide: the resolved harness
// packages at the versions of the parents in the module's chain, from a registry rather than a local path,
// and the libraries the harness decides at exactly the harness's versions.
import { HARNESS_PACKAGE, HARNESS_PACKAGES, HARNESS_VERSIONS_VARIABLE, isHarnessPackage } from './harness-packages.js'
import { asText, readKey } from './json-shapes.js'
import { type DirectDependency, LOCKFILE, type Lockfile, type ResolvedPackage } from './lockfile.js'
import { ROOT_MEMBER_PATH } from './workspace.js'

/**
 * The libraries whose version decides a verdict and which a member declares itself, because its own specs
 * import them or the tests gate runs them from the member. The harness's own manifest names their versions.
 */
const HARNESS_DECIDED: ReadonlySet<string> = new Set<string>([
  'vitest',
  '@vitest/coverage-v8',
  'typescript',
  // The shipped Playwright configuration and the member's own end-to-end specs must load one copy.
  '@playwright/test',
])

/** The libraries a member holding source declares, because the tests gate runs them from the member. */
const SUITE_LIBRARIES: readonly string[] = ['vitest', '@vitest/coverage-v8']

// pnpm protocols that resolve a package from the disk rather than from a registry.
const LOCAL_PROTOCOL: RegExp = /^(?:link|file|workspace|portal):/u

// The one local specifier allowed: this package's own link to itself, through which it runs its own gates.
const SELF_LINK: string = 'link:.'

/** What the wiring rules read for one member. */
export interface WiringInputs {
  readonly memberPath: string
  /** The member's parsed package.json. */
  readonly manifest: unknown
  /** The project's lockfile, or undefined when it is missing or unreadable. */
  readonly lockfile: Lockfile | undefined
  /** The versions the module's parents name, or undefined when they were not supplied. */
  readonly expected: ReadonlyMap<string, string> | undefined
  /** The versions the harness decides, by package name. */
  readonly pins: Readonly<Record<string, string>>
  /** Whether the member holds source the tests gate measures. */
  readonly hasSource: boolean
  /** The install-time rewrites the project configures or its lockfile records, judged at the project root. */
  readonly rewrites: readonly string[]
  /** Whether the registry configured for a package serves the resolution the lockfile records for it. */
  readonly isFromItsRegistry?: (resolved: ResolvedPackage) => boolean
}

// A harness package fetched from git, a directory or a stray tarball is not the release the parents name, even
// at the same version: no registry vouches for its content.
const harnessSourceFindings = (
  harness: readonly ResolvedPackage[],
  isFromItsRegistry: (resolved: ResolvedPackage) => boolean,
): readonly string[] =>
  harness
    .filter((resolved: ResolvedPackage): boolean => !isFromItsRegistry(resolved))
    .map(
      (resolved: ResolvedPackage): string =>
        `${LOCKFILE} resolves ${resolved.name}@${resolved.version} from a ${resolved.source} rather than from the ` +
        'registry configured for it; install the released package from its registry',
    )

// A harness package's own repository installs that package through its link to itself, as airness-node
// does and ploaness-node will, so its own name is the one harness package it may link.
const ownHarnessName = (manifest: unknown): string | undefined => {
  const name: string = asText(readKey(manifest, 'name'))
  return isHarnessPackage(name) ? name : undefined
}

const versionFinding = (name: string, found: string, expected: ReadonlyMap<string, string>): readonly string[] => {
  const wanted: string | undefined = expected.get(name)
  if (wanted === undefined) {
    return [`${LOCKFILE} resolves ${name} ${found}, but no parent in the module's chain names a version of it`]
  }
  return found === wanted
    ? []
    : [`${LOCKFILE} resolves ${name} ${found}, but the module's Airness parent names ${wanted}; install ${wanted}`]
}

// The checks that read the lockfile as a whole, made once, at the project root.
const projectFindings = (inputs: WiringInputs, lockfile: Lockfile): readonly string[] => {
  if (inputs.expected === undefined) {
    return [
      ...inputs.rewrites,
      `the versions of the module's Airness parents were not supplied in ${HARNESS_VERSIONS_VARIABLE}; ` +
        'run the gate through mvn verify, whose airness:node-gate reads them from the parent chain',
    ]
  }
  const expected: ReadonlyMap<string, string> = inputs.expected
  const harness: readonly ResolvedPackage[] = lockfile.packages.filter((resolved: ResolvedPackage): boolean =>
    isHarnessPackage(resolved.name),
  )
  const own: string | undefined = ownHarnessName(inputs.manifest)
  const self: readonly string[] =
    own === undefined ? [] : versionFinding(own, asText(readKey(inputs.manifest, 'version')), expected)
  const isHarnessResolved: boolean =
    own === HARNESS_PACKAGE || harness.some((resolved: ResolvedPackage): boolean => resolved.name === HARNESS_PACKAGE)
  return [
    ...inputs.rewrites,
    ...(isHarnessResolved
      ? []
      : [`${LOCKFILE} resolves no ${HARNESS_PACKAGE}; declare it as a development dependency and run pnpm install`]),
    ...self,
    ...harness.flatMap((resolved: ResolvedPackage): readonly string[] =>
      versionFinding(resolved.name, resolved.version, expected),
    ),
    ...harnessSourceFindings(
      harness,
      inputs.isFromItsRegistry ?? ((resolved: ResolvedPackage): boolean => resolved.source === 'registry'),
    ),
  ]
}

const localSpecifierFindings = (dependencies: readonly DirectDependency[], manifest: unknown): readonly string[] =>
  dependencies
    .filter(
      (dependency: DirectDependency): boolean =>
        HARNESS_PACKAGES.includes(dependency.name) &&
        (LOCAL_PROTOCOL.test(dependency.specifier) || LOCAL_PROTOCOL.test(dependency.version)) &&
        !(dependency.name === ownHarnessName(manifest) && dependency.specifier === SELF_LINK),
    )
    .map(
      (dependency: DirectDependency): string =>
        `package.json declares ${dependency.name} as "${dependency.specifier}"; ` +
        'install the released package by its version, which is what the gates are proven against',
    )

const pinFindings = (
  dependencies: readonly DirectDependency[],
  pins: Readonly<Record<string, string>>,
): readonly string[] =>
  dependencies.flatMap((dependency: DirectDependency): readonly string[] => {
    const pin: string | undefined = HARNESS_DECIDED.has(dependency.name) ? pins[dependency.name] : undefined
    return pin === undefined || pin === dependency.version
      ? []
      : [
          `${dependency.name} resolves to ${dependency.version}, but the harness runs ${pin}; ` +
            `declare it at exactly ${pin}`,
        ]
  })

const suiteFindings = (
  dependencies: readonly DirectDependency[],
  pins: Readonly<Record<string, string>>,
): readonly string[] =>
  SUITE_LIBRARIES.filter(
    (name: string): boolean => !dependencies.some((dependency: DirectDependency): boolean => dependency.name === name),
  ).map(
    (name: string): string =>
      `${name} is not declared; the tests gate runs the member's own copy, so declare it as a development ` +
      `dependency at ${pins[name] ?? 'the harness version'}`,
  )

/**
 * Every way one member departs from the harness wiring.
 * @param inputs what the member declares and the lockfile resolves.
 * @returns one finding per defect.
 */
export const wiringFindings = (inputs: WiringInputs): readonly string[] => {
  if (inputs.lockfile === undefined) {
    return [`${LOCKFILE} is missing or unreadable; the harness compares the versions a lockfile resolves`]
  }
  const dependencies: readonly DirectDependency[] | undefined = inputs.lockfile.importers.get(inputs.memberPath)
  if (dependencies === undefined) {
    return [`${LOCKFILE} has no importer for ${inputs.memberPath}; run pnpm install to bring the lockfile up to date`]
  }
  return [
    ...(inputs.memberPath === ROOT_MEMBER_PATH ? projectFindings(inputs, inputs.lockfile) : []),
    ...localSpecifierFindings(dependencies, inputs.manifest),
    ...pinFindings(dependencies, inputs.pins),
    ...(inputs.hasSource ? suiteFindings(dependencies, inputs.pins) : []),
  ]
}
