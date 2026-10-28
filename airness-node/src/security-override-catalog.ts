// Only the registered layer packages contribute approvals. A project cannot approve its own rewrite.
import { HARNESS_PACKAGES } from './harness-packages.js'
import { asRecord, asText, isArray } from './json-shapes.js'
import type { LayerPackage } from './layer.js'
import { splitPackageKey } from './lockfile.js'
import { SECURITY_OVERRIDES, type SecurityOverride, securitySelector } from './security-overrides.js'

const EXACT_VERSION: RegExp = /^\d+\.\d+\.\d+$/u
const PACKAGE_NAME: RegExp = /^(?:@[a-z0-9._-]+\/)?[a-z0-9][a-z0-9._-]*$/u
const ADVISORY: RegExp = /^(?:GHSA-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}|CVE-\d{4}-\d{4,})$/u
const DATE: RegExp = /^\d{4}-\d{2}-\d{2}$/u

const isValidParent = (parent: string): boolean => {
  const key: { readonly name: string; readonly version: string } = splitPackageKey(parent)
  return PACKAGE_NAME.test(key.name) && EXACT_VERSION.test(key.version) && parent === `${key.name}@${key.version}`
}

const advisoryNames = (raw: unknown): readonly string[] =>
  isArray(raw) && raw.every((entry: unknown): entry is string => typeof entry === 'string' && ADVISORY.test(entry))
    ? raw
    : []

const readApproval = (raw: unknown, layer: string): SecurityOverride => {
  const record: Record<string, unknown> = asRecord(raw)
  const entry: SecurityOverride = {
    parent: asText(record['parent']),
    dependency: asText(record['dependency']),
    version: asText(record['version']),
    advisories: advisoryNames(record['advisories']),
    reason: asText(record['reason']).trim(),
    addedOn: asText(record['addedOn']),
  }
  const isEdgeValid: boolean = isValidParent(entry.parent) && PACKAGE_NAME.test(entry.dependency)
  const isTargetValid: boolean = EXACT_VERSION.test(entry.version) && !HARNESS_PACKAGES.includes(entry.dependency)
  const isDocumented: boolean = entry.advisories.length > 0 && entry.reason.length > 0 && DATE.test(entry.addedOn)
  if (!(isEdgeValid && isTargetValid && isDocumented)) {
    throw new Error(`the stack layer ${layer} declares an invalid airness.layer.securityOverrides entry`)
  }
  return entry
}

const layerApprovals = (layer: LayerPackage): readonly SecurityOverride[] => {
  const block: Record<string, unknown> = asRecord(asRecord(asRecord(layer.manifest)['airness'])['layer'])
  const raw: unknown = block['securityOverrides']
  if (raw === undefined) {
    return []
  }
  if (!isArray(raw)) {
    throw new Error(`the stack layer ${layer.name} airness.layer.securityOverrides must be an array`)
  }
  return raw.map((entry: unknown): SecurityOverride => readApproval(entry, layer.name))
}

/** Combine the generic approvals with registered layers, refusing conflicting ownership of an edge. */
export const securityOverrideCatalog = (layers: readonly LayerPackage[]): readonly SecurityOverride[] => {
  const entries: readonly SecurityOverride[] = [
    ...SECURITY_OVERRIDES,
    ...layers.flatMap((layer: LayerPackage): readonly SecurityOverride[] => layerApprovals(layer)),
  ]
  const conflict: SecurityOverride | undefined = entries.find((entry: SecurityOverride): boolean =>
    entries.some(
      (other: SecurityOverride): boolean =>
        securitySelector(other) === securitySelector(entry) && other.version !== entry.version,
    ),
  )
  if (conflict !== undefined) {
    throw new Error(`stack layers disagree about the security replacement ${securitySelector(conflict)}`)
  }
  return entries
}
