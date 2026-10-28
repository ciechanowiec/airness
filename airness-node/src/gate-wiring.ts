// The harness installed as the module's parents decide: read from the project's lockfile, the harness's
// own manifest, and the versions airness:node-gate supplies from the module's parent chain.
import path from 'node:path'
import { coverageCandidates } from './coverage-policy.js'
import { failed, type GateResult, passed } from './exec.js'
import {
  HARNESS_VERSIONS_VARIABLE,
  MODULE_VARIABLE,
  MODULE_VERSION_VARIABLE,
  readHarnessVersions,
} from './harness-packages.js'
import { type InstallConfigInputs, rewriteFindings } from './install-config.js'
import { installConfigOf } from './install-config-files.js'
import { asRecord, asStringRecord, readKey } from './json-shapes.js'
import { layerPackagesFor } from './layer.js'
import { LOCKFILE, type Lockfile, type ResolvedPackage, readLockfile } from './lockfile.js'
import { isFromItsRegistry, type NpmConfig, npmConfigOf } from './npmrc.js'
import { harnessDirectory, type Member, readJson, readText } from './project.js'
import { securityOverrideCatalog } from './security-override-catalog.js'
import { type SecurityOverride, securityOverrideLockFindings } from './security-overrides.js'
import { wiringFindings } from './wiring-policy.js'
import { presentWorkingTreeFiles } from './working-tree.js'

/**
 * The versions the harness decides: the ones its own manifest depends on.
 * @returns each dependency of this package, by name.
 */
const harnessPins = (): Readonly<Record<string, string>> => {
  const manifest: unknown = readJson(path.join(harnessDirectory(), 'package.json'))
  return asStringRecord(readKey(manifest, 'dependencies'))
}

/** The member resolves the harness at its parents' versions, and the libraries the harness decides at its own. */
export const wiring = (member: Member): GateResult => {
  const text: string | undefined = readText(path.join(member.projectRoot, LOCKFILE))
  const lockfile: Lockfile | undefined = text === undefined ? undefined : readLockfile(text)
  const config: NpmConfig = npmConfigOf(member.projectRoot)
  const installation: InstallConfigInputs = installConfigOf(member.projectRoot)
  const approved: readonly SecurityOverride[] = securityOverrideCatalog(
    layerPackagesFor(member.root, member.packageJson, readJson),
  )
  const findings: readonly string[] = wiringFindings({
    memberPath: member.path,
    manifest: member.packageJson,
    lockfile,
    rewrites: [
      ...rewriteFindings(installation, approved),
      ...(member.path === '.' || readKey(readKey(member.packageJson, 'airness'), 'vulnerabilityAllowlist') === undefined
        ? []
        : ['airness.vulnerabilityAllowlist belongs in the root package.json of the declared Node project']),
      ...(lockfile?.rewrites ?? []),
      ...securityOverrideLockFindings(asRecord(installation.workspace)['overrides'], lockfile, approved),
    ],
    expected: readHarnessVersions(process.env[HARNESS_VERSIONS_VARIABLE]),
    moduleVersion: process.env[MODULE_VERSION_VARIABLE],
    module: process.env[MODULE_VARIABLE],
    pins: harnessPins(),
    isFromItsRegistry: (resolved: ResolvedPackage): boolean => isFromItsRegistry(config, resolved),
    hasSource:
      coverageCandidates(
        presentWorkingTreeFiles(member.root),
        member.settings,
        member.siblingPaths,
        member.hasOwnRuntime,
      ).length > 0,
  })
  return findings.length > 0
    ? failed(`${String(findings.length)} wiring defect(s)`, findings)
    : passed('the harness is installed at the versions the module and the harness decide')
}
