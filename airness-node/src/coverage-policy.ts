// Which files the coverage floor measures: executable TypeScript under the member's source roots, except
// the test tree, declarations, and the files a generator or a framework owns.
import { hasExtension, matchesGlob, normalisedSourceRoot } from './file-roles.js'
import type { DeclaredExclusion, Settings } from './settings.js'
import { memberFiles } from './source-selection.js'

// Every TypeScript format, components included: no facet measures a `.tsx` file in their place.
const EXECUTABLE_EXTENSIONS: readonly string[] = ['.ts', '.tsx', '.mts', '.cts']

const TEST_ROOT: string = 'tests'

/** Roles no coverage floor measures, whichever source root holds them. */
const COVERAGE_ROLE_EXCLUDE: readonly string[] = [
  `${TEST_ROOT}/**`,
  '**/node_modules/**',
  '**/*.d.ts',
  '**/*.d.mts',
  '**/*.d.cts',
  '**/*.spec.*',
  '**/*.test.*',
]

const coverageRoots = (sourceRoots: readonly string[]): readonly string[] =>
  [...new Set(sourceRoots.map((root: string): string => normalisedSourceRoot(root)))].filter(
    (root: string): boolean => root !== TEST_ROOT && !root.startsWith(`${TEST_ROOT}/`),
  )

const rootPrefix = (root: string): string => (root === '.' ? '' : `${root}/`)

/**
 * The globs of the files the floor measures, before exclusions.
 * @param sourceRoots the member's effective source roots.
 * @returns one glob per source root and executable extension.
 */
export const coverageIncludes = (sourceRoots: readonly string[]): readonly string[] =>
  coverageRoots(sourceRoots).flatMap((root: string): readonly string[] =>
    EXECUTABLE_EXTENSIONS.map((extension: string): string => `${rootPrefix(root)}**/*${extension}`),
  )

// The roles no setting can change, and the files a generator or a framework owns.
const roleExclusions = (settings: Settings): readonly string[] => [
  ...COVERAGE_ROLE_EXCLUDE,
  ...settings.generatedArtefacts,
  ...settings.frameworkGlue,
]

/**
 * The globs the floor never measures: the invariant roles, the files a generator or a framework owns, and
 * the files the member's `coverageExclude` names by role.
 * @param settings the member's settings.
 * @returns the exclusion globs.
 */
export const coverageExclusions = (settings: Settings): readonly string[] => [
  ...roleExclusions(settings),
  ...settings.coverageExclude,
]

const isMeasurable = (file: string, settings: Settings, exclusions: readonly string[]): boolean =>
  hasExtension(file, EXECUTABLE_EXTENSIONS) &&
  coverageRoots(settings.sourceRoots).some((root: string): boolean => file.startsWith(rootPrefix(root))) &&
  !exclusions.some((pattern: string): boolean => matchesGlob(pattern, file))

/**
 * The member's files the floor could measure before its `coverageExclude`, which an entry there must reach.
 * @param files the member-relative paths of the files a commit would carry.
 * @param settings the member's settings.
 * @param siblingPaths the members nested inside this one.
 * @returns the measurable files.
 */
export const measurableFiles = (
  files: readonly string[],
  settings: Settings,
  siblingPaths: readonly string[],
): readonly string[] =>
  memberFiles(files, siblingPaths).filter((file: string): boolean =>
    isMeasurable(file, settings, roleExclusions(settings)),
  )

/**
 * The member's files the floor measures, which decides whether it has a suite to run at all.
 * @param files the member-relative paths of the files a commit would carry.
 * @param settings the member's settings.
 * @param siblingPaths the members nested inside this one.
 * @returns the measured files.
 */
export const coverageCandidates = (
  files: readonly string[],
  settings: Settings,
  siblingPaths: readonly string[],
): readonly string[] =>
  memberFiles(files, siblingPaths).filter((file: string): boolean =>
    isMeasurable(file, settings, coverageExclusions(settings)),
  )

/**
 * Every coverage exclusion that reaches every file the floor would measure, which switches the floor off
 * rather than naming a role. One file left out of a member of one file is the same switch.
 * @param entries the member's `coverageExclude` entries.
 * @param measurable the files the floor would measure before them.
 * @returns one finding per such entry.
 */
export const blanketExclusionFindings = (
  entries: readonly DeclaredExclusion[],
  measurable: readonly string[],
): readonly string[] =>
  entries
    .filter(
      (entry: DeclaredExclusion): boolean =>
        measurable.length > 0 && measurable.every((file: string): boolean => matchesGlob(entry.pattern, file)),
    )
    .map(
      (entry: DeclaredExclusion): string =>
        `coverageExclude entry "${entry.pattern}" excludes every file the floor would measure; ` +
        'name the files of one role instead',
    )
