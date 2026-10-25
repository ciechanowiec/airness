// The harness's own npm packages, and the versions the module's Airness parents name for them.
import { LAYER_PACKAGES } from './layer.js'

/** The package this harness ships, which every node project resolves. */
export const HARNESS_PACKAGE: string = '@airness/node'

/** Every package a harness layer ships: this one and each stack layer above it. */
export const HARNESS_PACKAGES: readonly string[] = [HARNESS_PACKAGE, ...LAYER_PACKAGES]

/**
 * The environment variable `airness:node-gate` names the parents' versions in, as `name=version` pairs
 * separated by commas, such as `@airness/node=2.0.0,ploaness=1.4.0`. The versions come from the module's
 * Maven parent chain, which no file of the node project can state.
 */
export const HARNESS_VERSIONS_VARIABLE: string = 'AIRNESS_HARNESS_VERSIONS'

/**
 * The environment variable `airness:node-gate` names the module's own Maven version in. A harness package's
 * own repository releases the package at that version, which its parents need not name.
 */
export const MODULE_VERSION_VARIABLE: string = 'AIRNESS_MODULE_VERSION'

/**
 * The environment variable `airness:node-gate` names the module's own Maven identity in, as
 * `groupId:artifactId`, which no file of the node project can state.
 */
export const MODULE_VARIABLE: string = 'AIRNESS_MODULE'

/**
 * The one Maven module that builds each harness package, as `groupId:artifactId`. Only that module may
 * name its node project after the package, link it to itself and have it versioned as the module rather
 * than by a parent: any other module whose manifest names a harness package is posing as the harness.
 */
export const HARNESS_MODULES: ReadonlyMap<string, string> = new Map([
  [HARNESS_PACKAGE, 'eu.ciechanowiec:airness-node'],
  ['ploaness', 'eu.ciechanowiec:ploaness-node'],
])

/**
 * Whether a package name is one of the harness's own.
 * @param name the package name.
 * @returns true for a harness package.
 */
export const isHarnessPackage = (name: string): boolean => HARNESS_PACKAGES.includes(name)

/**
 * Read the versions the module's parents name.
 * @param text the variable's value, or undefined when it is not set.
 * @returns each package's expected version, none when the chain holds no Airness parent, or undefined when
 * the variable is not set or malformed.
 */
export const readHarnessVersions = (text: string | undefined): ReadonlyMap<string, string> | undefined => {
  if (text === undefined) {
    return undefined
  }
  if (text.trim().length === 0) {
    return new Map()
  }
  const pairs: readonly (readonly [string, string])[] = text
    .split(',')
    .map((pair: string): readonly [string, string] => {
      const at: number = pair.lastIndexOf('=')
      return at > 0 ? [pair.slice(0, at).trim(), pair.slice(at + 1).trim()] : ['', '']
    })
  return pairs.every(([name, version]: readonly [string, string]): boolean => name.length > 0 && version.length > 0)
    ? new Map(pairs)
    : undefined
}
