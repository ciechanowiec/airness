// The project-shaped half of the architecture contract, rendered into the rules dependency-cruiser reads:
// the Next.js application layer, which nothing imports back, the pure-logic floor a project or a layer
// package declares, and the production roots that import no test code and no development-only dependency. The
// framework-neutral rules stay in the shipped configuration.
import { productionSourceRoots } from './file-roles.js'
import { APP_ROOT } from './route-map.js'
import { DEFAULT_SOURCE_ROOTS } from './settings.js'

/** One forbidden rule, in dependency-cruiser's shape. */
export type ArchitectureRule = Readonly<Record<string, unknown>>

/**
 * The rule that keeps the Next.js App Router layer on top: it composes everything else, and nothing outside it
 * imports it. Phrased against everything outside the layer, so a new source directory is governed the moment
 * it exists.
 */
export const APPLICATION_LAYER_RULE: ArchitectureRule = {
  name: 'nothing-imports-app',
  severity: 'error',
  comment:
    `The Next.js App Router layer (${APP_ROOT}) composes everything else and nothing imports it back. ` +
    'Move what another module needs out of it, into a lower layer.',
  from: { path: '^src/', pathNot: `^${APP_ROOT}/` },
  to: { path: `^${APP_ROOT}/` },
}

// The routes directories Next.js also reads from the member root. The harness places a Next.js application under
// `src`: the application layer above, the entry points the coverage floor exempts, and the Next.js lint presets all
// name `src/app`, so a routes directory at the root would escape each of them at once.
const TOP_LEVEL_ROUTE_DIRECTORIES: readonly string[] = ['app', 'pages']

/**
 * The refusal of a Next.js routes directory at the member root, one finding per directory present.
 * @param isNextApp whether the member is a Next.js application.
 * @param files the member's files, member-relative and separated by forward slashes.
 * @returns what to move, or nothing for a member whose routes sit under `src`.
 */
export const misplacedRouteDirectoryFindings = (isNextApp: boolean, files: readonly string[]): readonly string[] =>
  isNextApp
    ? TOP_LEVEL_ROUTE_DIRECTORIES.filter((directory: string): boolean =>
        files.some((file: string): boolean => file.startsWith(`${directory}/`)),
      ).map(
        (directory: string): string =>
          `${directory}/: move the Next.js routes directory under src/ (src/${directory}/); the application layer, ` +
          'the coverage floor and the Next.js lint rules are held to src/, so a routes directory at the member ' +
          'root escapes all three',
      )
    : []

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

// What under a production root is test code or test configuration itself: a colocated spec, and the Vitest and
// Playwright files at the member root, which a member that declares the whole member as a root still holds.
const TEST_CODE: string = String.raw`\.(spec|test)\.(ts|tsx)$|^(tests|scripts)/|^vitest\.[^/]*$|^playwright\.config\.[^/]*$`

/**
 * The rule that keeps test code out of production: no module under a production source root imports a test
 * helper, fixture or fake from `tests`, whether by a relative path or an alias.
 * @param sourceRoots the member's source roots; `tests` and `scripts` are not production roots.
 * @returns the rule, or undefined when the member has no production root.
 */
export const productionImportsNoTestCodeRule = (sourceRoots: readonly string[]): ArchitectureRule | undefined => {
  const production: readonly string[] = productionSourceRoots(sourceRoots)
  if (production.length === 0) {
    return undefined
  }
  return {
    name: 'production-imports-no-test-code',
    severity: 'error',
    comment:
      `Production code under ${production.join(', ')} never imports test code: a helper, fixture or fake under ` +
      'tests would ship with it. Move what production needs out of tests, into a production module.',
    from: { path: `^(${alternation(production)})`, pathNot: TEST_CODE },
    to: { path: '^tests/' },
  }
}

// The build tooling configuration a member that declares the whole member as a root holds at its root, and the
// Storybook configuration beside it: each runs at development time only, so a development-only dependency is exactly
// what it imports.
const ROOT_TOOLING: string = String.raw`^(eslint|knip|lint-staged|postcss|prettier|stylelint|tailwind)\.config\.[^/]*$|^\.storybook/`

/**
 * The development-only dependency rule, rendered over every production source root rather than `src` alone: a
 * production install leaves a devDependency out, so production code under any declared root never imports one.
 * It replaces the shipped rule of the same name, which covers only `src`.
 * @param sourceRoots the member's source roots; `tests` and `scripts` are not production roots.
 * @returns the rule, or undefined when the member has no production root.
 */
export const productionImportsNoDevelopmentDependencyRule = (
  sourceRoots: readonly string[],
): ArchitectureRule | undefined => {
  const production: readonly string[] = productionSourceRoots(sourceRoots)
  if (production.length === 0) {
    return undefined
  }
  return {
    name: 'not-to-dev-dep',
    severity: 'error',
    comment:
      `Production source under ${production.join(', ')} never imports a development-only dependency, which a ` +
      'production install leaves out. Move the dependency, or the code.',
    from: { path: `^(${alternation(production)})`, pathNot: `${TEST_CODE}|${ROOT_TOOLING}` },
    to: { dependencyTypes: ['npm-dev'], dependencyTypesNot: ['type-only'], pathNot: ['node_modules/@types/'] },
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
  const production: ArchitectureRule | undefined = productionImportsNoTestCodeRule(sourceRoots)
  const developmentOnly: ArchitectureRule | undefined = productionImportsNoDevelopmentDependencyRule(sourceRoots)
  return [
    ...(isNextApp ? [APPLICATION_LAYER_RULE] : []),
    ...(floor === undefined ? [] : [floor]),
    ...(production === undefined ? [] : [production]),
    ...(developmentOnly === undefined ? [] : [developmentOnly]),
    ...cloudFrontRules(cloudFrontFiles),
  ]
}
