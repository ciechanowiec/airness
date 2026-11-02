// The members a node project holds, decided from its manifests rather than from labels it chooses.
import { JAVASCRIPT_EXTENSIONS, TYPESCRIPT_SOURCE_EXTENSIONS } from './file-roles.js'
import { declaredDependencies } from './json-shapes.js'
import { compileWorkspacePatterns, type WorkspacePattern } from './workspace-globs.js'
import { topLevelListItems } from './yaml-blocks.js'

/** The project-relative path of the member at the project root. */
export const ROOT_MEMBER_PATH: string = '.'

/** The workspace file key naming the directories pnpm treats as projects. */
const PACKAGES_KEY: string = 'packages'

/** The package whose declaration makes a member a Next application. */
const NEXT_PACKAGE: string = 'next'

/**
 * The directory patterns a workspace file declares.
 * @param workspaceFile the contents of pnpm-workspace.yaml, or an empty string when absent.
 * @returns one pattern per entry, in declaration order, including any leading-`!` exclusions.
 */
export const readWorkspacePackages = (workspaceFile: string): readonly string[] =>
  topLevelListItems(workspaceFile, PACKAGES_KEY)

// pnpm applies every exclusion after every inclusion rather than in written order, so a directory named
// by both is excluded whichever came first.
const isSelected = (patterns: readonly WorkspacePattern[], directory: string): boolean => {
  const isIncluded: boolean = patterns
    .filter((pattern: WorkspacePattern): boolean => !pattern.isExcluded)
    .some((pattern: WorkspacePattern): boolean => pattern.expression.test(`${directory}/package.json`))
  const isExcluded: boolean = patterns
    .filter((pattern: WorkspacePattern): boolean => pattern.isExcluded)
    .some((pattern: WorkspacePattern): boolean => pattern.expression.test(`${directory}/package.json`))
  return isIncluded && !isExcluded
}

/**
 * The directories a workspace file selects as members. The project root is always one: each workspace
 * member is checked as its own member, and so is the root that holds them.
 * @param patterns the declared directory patterns.
 * @param directoriesWithManifest every project-relative directory holding a package.json.
 * @returns the selected directories, root first, without duplicates.
 */
export const selectProjects = (
  patterns: readonly string[],
  directoriesWithManifest: readonly string[],
): readonly string[] => {
  const compiled: readonly WorkspacePattern[] = compileWorkspacePatterns(patterns)
  return [
    ROOT_MEMBER_PATH,
    ...directoriesWithManifest.filter(
      (directory: string): boolean => directory !== ROOT_MEMBER_PATH && isSelected(compiled, directory),
    ),
  ]
}

/**
 * The members nested inside one member, named relative to it, so an analyzer walking that member knows
 * where it stops.
 * @param memberPath the member, relative to the project root.
 * @param every every member path.
 * @returns the nested members, relative to `memberPath`.
 */
export const nestedWithin = (memberPath: string, every: readonly string[]): readonly string[] => {
  if (memberPath === ROOT_MEMBER_PATH) {
    return every.filter((other: string): boolean => other !== ROOT_MEMBER_PATH)
  }
  const prefix: string = `${memberPath}/`
  return every
    .filter((other: string): boolean => other.startsWith(prefix))
    .map((other: string): string => other.slice(prefix.length))
}

/**
 * Whether a member has a server of its own, which is what gives it JSX to hold to the accessibility
 * rules. Derived from what the member declares and never declared directly, so a project cannot receive
 * a weaker configuration by describing itself differently.
 * @param packageJson the member's parsed manifest.
 * @returns true when the member declares Next.
 */
export const hasOwnRuntime = (packageJson: unknown): boolean =>
  Object.hasOwn(declaredDependencies(packageJson), NEXT_PACKAGE)

/**
 * The patterns that stop an analyzer walking below this member into another member.
 * @param siblingPaths the nested members' paths, relative to this member's directory.
 * @returns one recursive ignore pattern per nested member.
 */
export const analysisBoundaries = (siblingPaths: readonly string[]): readonly string[] =>
  siblingPaths.map((sibling: string): string => `${sibling}/**`)

// The characters a glob reads as syntax, which a literal path escapes so that it names only itself.
const GLOB_SYNTAX: RegExp = /[\\*?[\]{}()!+@]/gu

/**
 * One ignore pattern per path Git ignores, so ESLint judges what a commit would carry, as every other gate does:
 * a directory, listed with its trailing slash, with everything under it, and a file alone.
 * @param ignoredPaths the ignored paths, relative to the member, each directory ending with a slash.
 * @returns the patterns, each path's glob syntax escaped.
 */
export const ignoredPatterns = (ignoredPaths: readonly string[]): readonly string[] =>
  ignoredPaths.map((ignored: string): string => {
    const literal: string = ignored.replaceAll(GLOB_SYNTAX, String.raw`\$&`)
    return literal.endsWith('/') ? `${literal}**` : literal
  })

/**
 * The arguments an ESLint run takes to judge a member: the harness configuration, the warning backstop,
 * and the boundary at every nested member.
 * @param config the absolute path of the harness configuration.
 * @param siblingPaths the nested members' paths, relative to this member's directory.
 * @param ignoredPaths the paths Git ignores in the member, relative to its directory.
 * @returns the full argument list, the target first.
 */
export const eslintArguments = (
  config: string,
  siblingPaths: readonly string[],
  ignoredPaths: readonly string[] = [],
): readonly string[] => [
  '.',
  '--config',
  config,
  '--max-warnings=0',
  '--ext',
  [...TYPESCRIPT_SOURCE_EXTENSIONS, ...JAVASCRIPT_EXTENSIONS].join(','),
  ...[...analysisBoundaries(siblingPaths), ...ignoredPatterns(ignoredPaths)].flatMap(
    (pattern: string): readonly string[] => ['--ignore-pattern', pattern],
  ),
]
