// An approved override changes one dependency edge to an exact, reviewed registry release. The
// catalogue belongs to the installed harness; the project's declarations and lockfile must agree.
import { asRecord, isArray, isRecord } from './json-shapes.js'
import { type Lockfile, splitPackageKey } from './lockfile.js'

/** One published security repair the harness permits in an installation. */
export interface SecurityOverride {
  readonly parent: string
  readonly dependency: string
  readonly version: string
  readonly advisories: readonly string[]
  readonly reason: string
  readonly addedOn: string
}

/** Generic tooling repairs, independent of a project's application stack. */
export const SECURITY_OVERRIDES: readonly SecurityOverride[] = [
  {
    parent: 'eslint-plugin-functional@10.0.1',
    dependency: 'deepmerge-ts',
    version: '8.0.2',
    advisories: ['GHSA-ggr8-5vv4-36mx'],
    reason:
      'The functional lint plugin still requests version 7; version 8 fixes recursive merges and retains ' +
      'the deepmerge API used to compose its rule options and schemas.',
    addedOn: '2026-10-28',
  },
]

/** The exact pnpm selector for the edge this approval covers. */
export const securitySelector = (entry: SecurityOverride): string => `${entry.parent}>${entry.dependency}`

const approvedEntry = (
  selector: string,
  version: unknown,
  approved: readonly SecurityOverride[],
): SecurityOverride | undefined =>
  approved.find((entry: SecurityOverride): boolean => securitySelector(entry) === selector && entry.version === version)

/** Reject malformed declarations and every replacement outside the installed harness's catalogue. */
export const securityOverrideFindings = (
  raw: unknown,
  approved: readonly SecurityOverride[],
  source: string,
): readonly string[] => {
  if (raw === undefined) {
    return []
  }
  if (!isRecord(raw) || isArray(raw)) {
    return [`${source} overrides must map approved dependency selectors to exact registry versions`]
  }
  return Object.entries(raw).flatMap(([selector, version]: readonly [string, unknown]): readonly string[] =>
    approvedEntry(selector, version, approved) === undefined
      ? [`${source} override ${selector}: the harness approves no such security replacement`]
      : [],
  )
}

const hasSameDeclarations = (left: unknown, right: unknown): boolean => {
  const entries: readonly (readonly [string, unknown])[] = Object.entries(asRecord(left))
  const other: Record<string, unknown> = asRecord(right)
  return entries.length === Object.keys(other).length && entries.every(([key, value]): boolean => other[key] === value)
}

const hasResolvedRepair = (dependencies: readonly string[], repair: SecurityOverride): boolean =>
  dependencies.some((dependency: string): boolean => {
    const resolved: { readonly name: string; readonly version: string } = splitPackageKey(dependency)
    return resolved.name === repair.dependency && resolved.version === repair.version
  })

const repairReach = (repair: SecurityOverride, lockfile: Lockfile): readonly string[] => {
  const parents: readonly (readonly [string, readonly string[]])[] = [...lockfile.edges].filter(([key]): boolean => {
    const parent: { readonly name: string; readonly version: string } = splitPackageKey(key)
    return `${parent.name}@${parent.version}` === repair.parent
  })
  if (parents.length === 0) {
    return [`pnpm-lock.yaml override ${securitySelector(repair)} reaches no resolved parent; remove the stale entry`]
  }
  return parents.every(([, dependencies]): boolean => hasResolvedRepair(dependencies, repair))
    ? []
    : [`pnpm-lock.yaml override ${securitySelector(repair)} does not resolve the approved ${repair.version}`]
}

/** Prove that the locked replacements match the declarations and actually repair every selected edge. */
export const securityOverrideLockFindings = (
  declared: unknown,
  lockfile: Lockfile | undefined,
  approved: readonly SecurityOverride[],
): readonly string[] => {
  if (lockfile === undefined) {
    return []
  }
  return [
    ...securityOverrideFindings(lockfile.overrides, approved, 'pnpm-lock.yaml'),
    ...(hasSameDeclarations(declared, lockfile.overrides)
      ? []
      : ['pnpm-lock.yaml security overrides differ from pnpm-workspace.yaml; regenerate the lockfile']),
    ...Object.entries(asRecord(lockfile.overrides)).flatMap(
      ([selector, version]: readonly [string, unknown]): readonly string[] => {
        const repair: SecurityOverride | undefined = approvedEntry(selector, version, approved)
        return repair === undefined ? [] : repairReach(repair, lockfile)
      },
    ),
  ]
}
