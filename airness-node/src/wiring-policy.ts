// Whether a node project installs the harness the way its Maven parents decide: the resolved harness
// packages at the versions of the parents in the module's chain, from a registry rather than a local path,
// and the libraries the harness decides at exactly the harness's versions.
import {
  HARNESS_MODULES,
  HARNESS_PACKAGE,
  HARNESS_PACKAGES,
  HARNESS_VERSIONS_VARIABLE,
  isHarnessPackage,
  MODULE_VARIABLE,
  MODULE_VERSION_VARIABLE,
} from './harness-packages.js'
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
  /** The module's own Maven version, which a harness package's own repository releases it at. */
  readonly moduleVersion?: string | undefined
  /** The module's own Maven identity, `groupId:artifactId`, which decides whether it builds a harness package. */
  readonly module?: string | undefined
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
// and ploaness-node do, so its own name is the one harness package it may link. Which repository that is
// comes from the module's Maven identity, never from the manifest: a manifest's name is the project's to write.
const ownHarnessName = (manifest: unknown, module: string | undefined): string | undefined => {
  const name: string = asText(readKey(manifest, 'name'))
  return module !== undefined && isHarnessPackage(name) && HARNESS_MODULES.get(name) === module ? name : undefined
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

// A harness package's own repository releases the package at the module's own Maven version, and its chain
// need not name it: ploaness-node inherits airness-parent-node, which names only @airness/node. A chain that
// does name it, as airness-node's does, still holds it to the parent's version too.
const selfFindings = (
  manifest: unknown,
  own: string,
  moduleVersion: string | undefined,
  expected: ReadonlyMap<string, string>,
): readonly string[] => {
  const found: string = asText(readKey(manifest, 'version'))
  const named: readonly string[] = expected.has(own) ? versionFinding(own, found, expected) : []
  if (moduleVersion === undefined) {
    return [
      `the module's own version was not supplied in ${MODULE_VERSION_VARIABLE}; run the gate through mvn ` +
        'verify, whose airness:node-gate reads it from the module',
      ...named,
    ]
  }
  return found === moduleVersion
    ? named
    : [
        `package.json versions ${own} ${found}, but the module that builds it is ${moduleVersion}; ` +
          "release the package at the module's version",
        ...named,
      ]
}

// A member named after a harness package is that package's own repository, or a project posing as it.
const identityFindings = (inputs: WiringInputs): readonly string[] => {
  const name: string = asText(readKey(inputs.manifest, 'name'))
  if (!isHarnessPackage(name)) {
    return []
  }
  if (ownHarnessName(inputs.manifest, inputs.module) === undefined) {
    const module: string = inputs.module ?? `not named in ${MODULE_VARIABLE}`
    return [
      `package.json names the package ${name}, which only the module ${String(HARNESS_MODULES.get(name))} ` +
        `builds, and this module is ${module}; give the package a ` +
        'name of its own',
    ]
  }
  return selfFindings(inputs.manifest, name, inputs.moduleVersion, inputs.expected ?? new Map())
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
  const own: string | undefined = ownHarnessName(inputs.manifest, inputs.module)
  const isHarnessResolved: boolean =
    own === HARNESS_PACKAGE || harness.some((resolved: ResolvedPackage): boolean => resolved.name === HARNESS_PACKAGE)
  return [
    ...inputs.rewrites,
    ...(isHarnessResolved
      ? []
      : [`${LOCKFILE} resolves no ${HARNESS_PACKAGE}; declare it as a development dependency and run pnpm install`]),
    ...harness.flatMap((resolved: ResolvedPackage): readonly string[] =>
      versionFinding(resolved.name, resolved.version, expected),
    ),
    ...harnessSourceFindings(
      harness,
      inputs.isFromItsRegistry ?? ((resolved: ResolvedPackage): boolean => resolved.source === 'registry'),
    ),
  ]
}

const localSpecifierFindings = (
  dependencies: readonly DirectDependency[],
  manifest: unknown,
  module: string | undefined,
): readonly string[] =>
  dependencies
    .filter(
      (dependency: DirectDependency): boolean =>
        HARNESS_PACKAGES.includes(dependency.name) &&
        (LOCAL_PROTOCOL.test(dependency.specifier) || LOCAL_PROTOCOL.test(dependency.version)) &&
        !(dependency.name === ownHarnessName(manifest, module) && dependency.specifier === SELF_LINK),
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
    ...identityFindings(inputs),
    ...localSpecifierFindings(dependencies, inputs.manifest, inputs.module),
    ...pinFindings(dependencies, inputs.pins),
    ...(inputs.hasSource ? suiteFindings(dependencies, inputs.pins) : []),
  ]
}
