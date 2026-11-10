// Which files the coverage floor measures: executable TypeScript under the member's source roots, except
// the test tree, declarations, and the files a generator or a framework owns.
import { hasExtension, matchesGlob, normalisedSourceRoot, TYPESCRIPT_SOURCE_EXTENSIONS } from './file-roles.js'
import type { DeclaredExclusion, Settings } from './settings.js'
import { memberFiles } from './source-selection.js'

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
    TYPESCRIPT_SOURCE_EXTENSIONS.map((extension: string): string => `${rootPrefix(root)}**/*${extension}`),
  )

// The App Router files the e2e crawl renders on every page it audits, and the proxy every response it reads
// passes through, which the security-header spec judges. They are framework glue the running application
// verifies. Route handlers, metadata routes and the error, forbidden and unauthorized boundaries are not driven
// by any shipped spec, so they stay under the floor like any other logic; so do colocated helpers and
// components.
const APP_ENTRY_NAMES: readonly string[] = ['page', 'layout', 'template', 'loading', 'not-found', 'global-not-found']

const NEXT_ENTRY_EXCLUDE: readonly string[] = [
  ...APP_ENTRY_NAMES.flatMap((name: string): readonly string[] => [`src/app/**/${name}.ts`, `src/app/**/${name}.tsx`]),
  'src/proxy.ts',
]

// The roles no setting can change, the files a generator or a framework owns, and a Next.js application's
// entry points.
const roleExclusions = (settings: Settings, isNextApp: boolean): readonly string[] => [
  ...COVERAGE_ROLE_EXCLUDE,
  ...settings.generatedArtefacts,
  ...settings.frameworkGlue,
  ...(isNextApp ? NEXT_ENTRY_EXCLUDE : []),
]

/**
 * The globs the floor never measures: the invariant roles, the files a generator or a framework owns, and
 * the files the member's `coverageExclude` names by role.
 * @param settings the member's settings.
 * @param isNextApp whether the member is a Next.js application, whose entry points the e2e gate verifies.
 * @returns the exclusion globs.
 */
export const coverageExclusions = (settings: Settings, isNextApp: boolean = false): readonly string[] => [
  ...roleExclusions(settings, isNextApp),
  ...settings.coverageExclude,
]

const isMeasurable = (file: string, settings: Settings, exclusions: readonly string[]): boolean =>
  hasExtension(file, TYPESCRIPT_SOURCE_EXTENSIONS) &&
  coverageRoots(settings.sourceRoots).some((root: string): boolean => file.startsWith(rootPrefix(root))) &&
  !exclusions.some((pattern: string): boolean => matchesGlob(pattern, file))

/**
 * The member's files the floor could measure before its `coverageExclude`, which an entry there must reach.
 * @param files the member-relative paths of the files a commit would carry.
 * @param settings the member's settings.
 * @param siblingPaths the members nested inside this one.
 * @param isNextApp whether the member is a Next.js application, whose entry points the e2e gate verifies.
 * @returns the measurable files.
 */
export const measurableFiles = (
  files: readonly string[],
  settings: Settings,
  siblingPaths: readonly string[],
  isNextApp: boolean = false,
): readonly string[] =>
  memberFiles(files, siblingPaths).filter((file: string): boolean =>
    isMeasurable(file, settings, roleExclusions(settings, isNextApp)),
  )

/**
 * The member's files the floor measures, which decides whether it has a suite to run at all.
 * @param files the member-relative paths of the files a commit would carry.
 * @param settings the member's settings.
 * @param siblingPaths the members nested inside this one.
 * @param isNextApp whether the member is a Next.js application, whose entry points the e2e gate verifies.
 * @returns the measured files.
 */
export const coverageCandidates = (
  files: readonly string[],
  settings: Settings,
  siblingPaths: readonly string[],
  isNextApp: boolean = false,
): readonly string[] =>
  memberFiles(files, siblingPaths).filter((file: string): boolean =>
    isMeasurable(file, settings, coverageExclusions(settings, isNextApp)),
  )

const reachesEvery = (patterns: readonly string[], measurable: readonly string[]): boolean =>
  measurable.length > 0 &&
  measurable.every((file: string): boolean => patterns.some((pattern: string): boolean => matchesGlob(pattern, file)))

const BLANKET_REPAIR: string = 'name the files of one role instead'

// The entries together, when none of them alone is the switch: `src/**/*.ts` beside `src/**/*.tsx` leaves the
// floor nothing to measure, and so no suite to run, exactly as `src/**` does.
const unionFindings = (entries: readonly DeclaredExclusion[], measurable: readonly string[]): readonly string[] => {
  const patterns: readonly string[] = entries.map((entry: DeclaredExclusion): string => entry.pattern)
  const named: string = patterns.map((pattern: string): string => JSON.stringify(pattern)).join(', ')
  return reachesEvery(patterns, measurable)
    ? [`coverageExclude entries ${named} together exclude every file the floor would measure; ${BLANKET_REPAIR}`]
    : []
}

/**
 * Every coverage exclusion that reaches every file the floor would measure, which switches the floor off
 * rather than naming a role. One file left out of a member of one file is the same switch, and so are
 * entries that reach every file between them.
 * @param entries the member's `coverageExclude` entries.
 * @param measurable the files the floor would measure before them.
 * @returns one finding per such entry, or one naming every entry when only their union is the switch.
 */
export const blanketExclusionFindings = (
  entries: readonly DeclaredExclusion[],
  measurable: readonly string[],
): readonly string[] => {
  const blanket: readonly string[] = entries
    .filter((entry: DeclaredExclusion): boolean => reachesEvery([entry.pattern], measurable))
    .map(
      (entry: DeclaredExclusion): string =>
        `coverageExclude entry "${entry.pattern}" excludes every file the floor would measure; ${BLANKET_REPAIR}`,
    )
  return blanket.length > 0 ? blanket : unionFindings(entries, measurable)
}
