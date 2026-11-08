// The release-age floor, judged once at the project root: the settings pnpm enforces it with, and the
// publication time of every release the lockfile resolves, read on every run from the registry pnpm
// fetched it from. A release no registry dates is reported, never passed over.
import path from 'node:path'
import { failed, type GateContext, type GateResult, passed } from './gate-result.js'
import { isHarnessPackage } from './harness-packages.js'
import { LOCKFILE, type Lockfile, lockfileDefect, type ResolvedPackage, readLockfile } from './lockfile.js'
import { isFromItsRegistry, type NpmConfig, npmConfigOf, registryFor, tokenFor } from './npmrc.js'
import type { Member } from './project.js'
import { readText } from './project.js'
import { redactUrls } from './redaction.js'
import { type Lookup, publicationTimesOf, type Release } from './registry.js'
import { type PublishedRelease, releaseAgeSettingFindings, youngReleaseFindings } from './release-age.js'
import { ROOT_MEMBER_PATH } from './workspace.js'
import { readWorkspaceFile } from './workspace-file.js'

// One finding per cause rather than per release, so an unreachable registry reads as one problem.
const unreadFindings = (lookups: readonly Lookup[]): readonly string[] => {
  const failures: readonly Lookup[] = lookups.filter((lookup: Lookup): boolean => lookup.problem !== undefined)
  return [...new Set(failures.map((lookup: Lookup): string => String(lookup.problem)))].map(
    (problem: string): string => {
      const affected: readonly Lookup[] = failures.filter((lookup: Lookup): boolean => lookup.problem === problem)
      const [first] = affected
      return (
        `could not read when ${String(affected.length)} release(s) were published, ` +
        `${String(first?.name)}@${String(first?.version)} first: ${problem}. release-age reads the registry ` +
        'on every run and fails rather than pass without it'
      )
    },
  )
}

/**
 * The finding for a release no registry serves, its URL stripped of credentials.
 * @param resolved how the lockfile resolves the release.
 * @returns the finding.
 */
export const undatedFinding = (resolved: ResolvedPackage): string => {
  const where: string = resolved.tarball.length > 0 ? ` (${redactUrls(resolved.tarball)})` : ''
  return (
    `${resolved.name}@${resolved.version} resolves from a ${resolved.source}${where}, which no registry dates, ` +
    'so no release-age floor can measure it; install it from a registry'
  )
}

const releaseOf = (resolved: ResolvedPackage, config: NpmConfig): Release => {
  const registry: string = registryFor(config, resolved.name)
  const credential: string | undefined = tokenFor(config, registry)
  return { name: resolved.name, version: resolved.version, registry, token: credential }
}

/**
 * The release-age floor holds: pnpm is set to enforce it, every resolved release comes from a registry, and
 * none is younger than the floor.
 * @param member the member judged; the project root judges for every member.
 * @param _context the run's context, which the floor does not read.
 * @param environment the variables the npm configuration and the proxy are read from.
 * @param now the instant to measure against.
 * @returns the gate result.
 */
export const releaseAge = async (
  member: Member,
  _context?: GateContext,
  environment: NodeJS.ProcessEnv = process.env,
  now: number = Date.now(),
): Promise<GateResult> => {
  if (member.path !== ROOT_MEMBER_PATH) {
    return passed('the project root judges the release age of every member')
  }
  const text: string | undefined = readText(path.join(member.projectRoot, LOCKFILE))
  const lockfile: Lockfile | undefined = text === undefined ? undefined : readLockfile(text)
  if (lockfile === undefined) {
    return failed('the lockfile cannot be read', [
      `${lockfileDefect(text)}; ${LOCKFILE} names the releases this gate measures`,
    ])
  }
  const config: NpmConfig = npmConfigOf(member.projectRoot, environment)
  // Every release, harness packages included, must come from a registry. Only the age floor leaves the harness
  // packages out, since a harness release is installed at the version its Maven parent names.
  const judged: readonly ResolvedPackage[] = lockfile.packages
  const dated: readonly ResolvedPackage[] = judged.filter((resolved: ResolvedPackage): boolean =>
    isFromItsRegistry(config, resolved),
  )
  const measured: readonly ResolvedPackage[] = dated.filter(
    (resolved: ResolvedPackage): boolean => !isHarnessPackage(resolved.name),
  )
  const lookups: readonly Lookup[] = await publicationTimesOf(
    measured.map((resolved: ResolvedPackage): Release => releaseOf(resolved, config)),
  )
  const findings: readonly string[] = [
    ...releaseAgeSettingFindings(readWorkspaceFile(member.projectRoot)),
    ...judged
      .filter((resolved: ResolvedPackage): boolean => !dated.includes(resolved))
      .map((resolved: ResolvedPackage): string => undatedFinding(resolved)),
    ...youngReleaseFindings(
      lookups.flatMap((lookup: Lookup): readonly PublishedRelease[] =>
        lookup.publishedAt === undefined ? [] : [{ ...lookup, publishedAt: lookup.publishedAt }],
      ),
      now,
    ),
    ...unreadFindings(lookups),
  ]
  return findings.length > 0
    ? failed(`${String(findings.length)} release-age defect(s)`, findings)
    : passed(`each of the ${String(measured.length)} resolved release(s) is older than the floor, by its registry`)
}
