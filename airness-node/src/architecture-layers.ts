// The project-shaped half of the architecture contract, rendered into the rules dependency-cruiser reads:
// the Next.js application layer, which nothing imports back, and the pure-logic floor a project or a layer
// package declares. The framework-neutral rules stay in the shipped configuration.
import { DEFAULT_SOURCE_ROOTS } from './settings.js'

/** One forbidden rule, in dependency-cruiser's shape. */
export type ArchitectureRule = Readonly<Record<string, unknown>>

/** The Next.js App Router directory, the top layer of a Next.js application. */
const APPLICATION_LAYER: string = 'src/app'

/**
 * The rule that keeps the Next.js App Router layer on top: it composes everything else, and nothing outside it
 * imports it. Phrased against everything outside the layer, so a new source directory is governed the moment
 * it exists.
 */
export const APPLICATION_LAYER_RULE: ArchitectureRule = {
  name: 'nothing-imports-app',
  severity: 'error',
  comment:
    `The Next.js App Router layer (${APPLICATION_LAYER}) composes everything else and nothing imports it back. ` +
    'Move what another module needs out of it, into a lower layer.',
  from: { path: '^src/', pathNot: `^${APPLICATION_LAYER}/` },
  to: { path: `^${APPLICATION_LAYER}/` },
}

// Every root is spliced into a regular expression, so it is escaped on the way in: a directory such as
// `src/app/(site)` would otherwise read as a capture group, and a glob-shaped root as an invalid expression.
const REGEX_METACHARACTERS: RegExp = /[$()*+.?[\\\]^{|}]/gu

const escaped = (character: string): string => `\\${character}`

const asRoot = (directory: string): string =>
  `${directory.replaceAll(REGEX_METACHARACTERS, (character: string): string => escaped(character))}/`

const alternation = (directories: readonly string[]): string =>
  directories.map((directory: string): string => (directory === '.' ? '' : asRoot(directory))).join('|')

/**
 * The rule that keeps the declared pure-logic directories pure: they depend on nothing else under a source
 * root, only on types and third-party libraries, which is what keeps them testable without mocks.
 * @param pureLogicRoots the directories forming the floor, member-relative and without a trailing slash.
 * @param sourceRoots the member's source roots.
 * @returns the rule, or undefined when nothing declares a floor.
 */
export const pureLogicRule = (
  pureLogicRoots: readonly string[],
  sourceRoots: readonly string[] = DEFAULT_SOURCE_ROOTS,
): ArchitectureRule | undefined => {
  if (pureLogicRoots.length === 0) {
    return undefined
  }
  const floor: string = alternation(pureLogicRoots)
  return {
    name: 'pure-logic-stays-pure',
    severity: 'error',
    comment:
      `${pureLogicRoots.join(' and ')} are the pure-logic floor: they must depend on nothing else under ` +
      `${sourceRoots.join(', ')} (only types and third-party libraries). Pass framework data in as a plain value.`,
    from: { path: `^(${floor})` },
    to: { path: `^(${alternation(sourceRoots)})`, pathNot: `^(${floor})`, dependencyTypesNot: ['type-only'] },
  }
}

// The module the CloudFront Functions runtime provides itself, which no package and no file supplies.
const CLOUDFRONT_MODULE: string = 'cloudfront'

const UNRESOLVABLE_RULE: string = 'not-to-unresolvable'

const UNRESOLVABLE_COMMENT: string =
  'An import that does not resolve is a broken path: a typo, a renamed file, a missing dependency.'

const exactFiles = (files: readonly string[]): string =>
  `^(${files.map((file: string): string => file.replaceAll(REGEX_METACHARACTERS, (character: string): string => escaped(character))).join('|')})$`

/**
 * The unresolvable-import rule, split so that the files a project declares for the CloudFront Functions runtime
 * may import the module that runtime provides, and nothing else may. The first rule replaces the shipped one of
 * the same name.
 * @param cloudFrontFiles the member's declared CloudFront files, member-relative.
 * @returns the two rules, or none when the member declares no CloudFront file.
 */
export const cloudFrontRules = (cloudFrontFiles: readonly string[]): readonly ArchitectureRule[] => {
  if (cloudFrontFiles.length === 0) {
    return []
  }
  const files: string = exactFiles(cloudFrontFiles)
  return [
    {
      name: UNRESOLVABLE_RULE,
      severity: 'error',
      comment: UNRESOLVABLE_COMMENT,
      from: { pathNot: files },
      to: { couldNotResolve: true },
    },
    {
      name: `${UNRESOLVABLE_RULE}-cloudfront`,
      severity: 'error',
      comment: `${UNRESOLVABLE_COMMENT} A CloudFront function may import only the ${CLOUDFRONT_MODULE} module its runtime provides.`,
      from: { path: files },
      to: { couldNotResolve: true, pathNot: `^${CLOUDFRONT_MODULE}$` },
    },
  ]
}

/**
 * The project-shaped rules one member is held to.
 * @param isNextApp whether the member is a Next.js application.
 * @param pureLogicRoots the member's declared pure-logic floor.
 * @param sourceRoots the member's source roots.
 * @param cloudFrontFiles the member's declared CloudFront Functions files, member-relative.
 * @returns the rules to add to the shipped configuration.
 */
export const layerRules = (
  isNextApp: boolean,
  pureLogicRoots: readonly string[],
  sourceRoots: readonly string[],
  cloudFrontFiles: readonly string[] = [],
): readonly ArchitectureRule[] => {
  const floor: ArchitectureRule | undefined = pureLogicRule(pureLogicRoots, sourceRoots)
  return [
    ...(isNextApp ? [APPLICATION_LAYER_RULE] : []),
    ...(floor === undefined ? [] : [floor]),
    ...cloudFrontRules(cloudFrontFiles),
  ]
}
