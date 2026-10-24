// The release-age floor: no dependency is installed until it has been public for a day, so a compromised
// publish is usually pulled before anything installs it. pnpm enforces the floor at install time when the
// workspace file sets it, and the gate reads the registry for every resolved release on every run.
import { isHarnessPackage } from './harness-packages.js'
import { isArray, isRecord } from './json-shapes.js'
import { splitPackageKey } from './lockfile.js'

/** The floor, in the minutes pnpm's `minimumReleaseAge` is written in: one day. */
const RELEASE_AGE_FLOOR_MINUTES: number = 1440

const MILLISECONDS_PER_MINUTE: number = 60_000
const MINUTES_PER_HOUR: number = 60
const WORKSPACE_FILE: string = 'pnpm-workspace.yaml'

const floorFinding = (workspace: Record<string, unknown>): readonly string[] => {
  const floor: unknown = workspace['minimumReleaseAge']
  return typeof floor === 'number' && floor >= RELEASE_AGE_FLOOR_MINUTES
    ? []
    : [
        `${WORKSPACE_FILE} minimumReleaseAge: must be at least ${String(RELEASE_AGE_FLOOR_MINUTES)} (a day, in ` +
          'minutes), so pnpm refuses a release younger than that',
      ]
}

const strictFinding = (workspace: Record<string, unknown>): readonly string[] =>
  workspace['minimumReleaseAgeStrict'] === true
    ? []
    : [
        `${WORKSPACE_FILE} minimumReleaseAgeStrict: must be true; without it pnpm installs a release younger ` +
          'than the floor and writes an exclusion for it rather than refusing',
      ]

const exclusionFindings = (workspace: Record<string, unknown>): readonly string[] => {
  const exclusions: unknown = workspace['minimumReleaseAgeExclude']
  return (isArray(exclusions) ? exclusions : [])
    .map(String)
    .filter((entry: string): boolean => !isHarnessPackage(splitPackageKey(entry).name))
    .map(
      (entry: string): string =>
        `${WORKSPACE_FILE} minimumReleaseAgeExclude: names ${entry}; only a harness package may be excluded ` +
        'from the floor, so wait for the release to age instead',
    )
}

/**
 * Every way the workspace file weakens the floor pnpm enforces.
 * @param workspace the parsed pnpm-workspace.yaml, or undefined when the project has none.
 * @returns one finding per defect.
 */
export const releaseAgeSettingFindings = (workspace: unknown): readonly string[] => {
  const settings: Record<string, unknown> = isRecord(workspace) ? workspace : {}
  return [...floorFinding(settings), ...strictFinding(settings), ...exclusionFindings(settings)]
}

/** A resolved release and when the registry says it was published. */
export interface PublishedRelease {
  readonly name: string
  readonly version: string
  /** Epoch milliseconds. */
  readonly publishedAt: number
}

/**
 * Every release younger than the floor.
 * @param releases the resolved releases with their publication instants.
 * @param now the instant to measure against, in epoch milliseconds.
 * @returns one finding per release still inside the floor.
 */
export const youngReleaseFindings = (releases: readonly PublishedRelease[], now: number): readonly string[] =>
  releases
    .filter(
      (release: PublishedRelease): boolean =>
        now - release.publishedAt < RELEASE_AGE_FLOOR_MINUTES * MILLISECONDS_PER_MINUTE,
    )
    .map((release: PublishedRelease): string => {
      const hours: number = Math.floor((now - release.publishedAt) / (MILLISECONDS_PER_MINUTE * MINUTES_PER_HOUR))
      return (
        `${release.name}@${release.version} was published ${String(hours)} hour(s) ago; a release must be ` +
        `${String(RELEASE_AGE_FLOOR_MINUTES / MINUTES_PER_HOUR)} hours old before it is installed, so pin an ` +
        'older version or wait'
      )
    })
