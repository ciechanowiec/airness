// Carve-outs that reach nothing: a concrete file a tool configuration names that no longer exists, and a
// declared exclusion that excludes no file. Both leave a report reading exactly as it would without them.
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import {
  type ConfigReferenceViolation,
  extractLiteralSourcePaths,
  findMissingConfigReferences,
} from './config-references.js'
import { blanketExclusionFindings, measurableFiles } from './coverage-policy.js'
import { failed, type GateResult, passed } from './exec.js'
import { harnessDirectory, type Member } from './project.js'
import { skipRouteFindings } from './route-exclusions.js'
import { appRootOf, type DeclaredRoute, declaredRoutesOf } from './route-map.js'
import {
  type DeclaredExclusion,
  findConvenienceExclusions,
  findUnreachedExclusions,
  findUnreachedExclusionsBySetting,
  VITEST_CONFIG_FILE,
} from './settings.js'
import { memberFiles } from './source-selection.js'
import { presentWorkingTreeFiles } from './working-tree.js'

// The shipped configurations and any the project still holds. A carve-out in the shipped one names a
// project path, so it rots when the project renames that file.
const configTargets = (member: Member): readonly string[] =>
  [
    path.join(harnessDirectory(), 'configs', 'airness-biome.json'),
    path.join(harnessDirectory(), 'configs', 'knip.json'),
    path.join(harnessDirectory(), 'configs', 'dependency-cruiser.json'),
    path.join(member.root, 'biome.json'),
    path.join(member.root, VITEST_CONFIG_FILE),
  ].filter((file: string): boolean => existsSync(file))

const danglingReferences = (member: Member): readonly string[] => {
  const isExistingFile = (relativePath: string): boolean => existsSync(path.join(member.root, relativePath))
  return configTargets(member).flatMap((configPath: string): readonly string[] =>
    findMissingConfigReferences(extractLiteralSourcePaths(readFileSync(configPath, 'utf8')), isExistingFile).map(
      (violation: ConfigReferenceViolation): string =>
        `${path.basename(configPath)}: ${violation.path} (${violation.reason})`,
    ),
  )
}

// The JavaScript allowlist's unexplained entries are the javascript-allowlist gate's to report.
const unexplained = (member: Member): readonly string[] =>
  findConvenienceExclusions(
    member.settings.declaredExclusions.filter(
      (entry: DeclaredExclusion): boolean => entry.setting !== 'javascriptAllowlist',
    ),
  )

const isCoverageEntry = (entry: DeclaredExclusion): boolean => entry.setting === 'coverageExclude'

// A coverage exclusion has to reach a file the floor would otherwise measure; every other setting a file of
// its own kind the member carries.
const unreached = (member: Member): readonly string[] => {
  const files: readonly string[] = presentWorkingTreeFiles(member.root)
  const entries: readonly DeclaredExclusion[] = member.settings.declaredExclusions
  const coverage: readonly DeclaredExclusion[] = entries.filter((entry: DeclaredExclusion): boolean =>
    isCoverageEntry(entry),
  )
  const measurable: readonly string[] = measurableFiles(
    files,
    member.settings,
    member.siblingPaths,
    member.hasOwnRuntime,
  )
  return [
    ...findUnreachedExclusions(coverage, measurable),
    ...blanketExclusionFindings(coverage, measurable),
    ...findUnreachedExclusionsBySetting(
      entries.filter((entry: DeclaredExclusion): boolean => !isCoverageEntry(entry)),
      memberFiles(files, member.siblingPaths),
    ),
  ]
}

// The pages a Next.js application's App Router declares, which a skipped route prefix is judged against.
const declaredPages = (member: Member): readonly DeclaredRoute[] | undefined => {
  if (!member.hasOwnRuntime) {
    return undefined
  }
  const files: readonly string[] = memberFiles(presentWorkingTreeFiles(member.root), member.siblingPaths)
  const appRoot: string | undefined = appRootOf(files)
  return appRoot === undefined ? undefined : declaredRoutesOf(files, appRoot)
}

/** Every carve-out of a tool configuration or a setting reaches a file that exists. */
export const configReferences = (member: Member): GateResult => {
  const findings: readonly string[] = [
    ...danglingReferences(member),
    ...unexplained(member),
    ...unreached(member),
    ...skipRouteFindings(member.settings.declaredExclusions, declaredPages(member)),
  ]
  return findings.length > 0
    ? failed(`${String(findings.length)} carve-out(s) that reach nothing`, findings)
    : passed('every carve-out of a tool configuration or setting reaches a file that exists')
}
