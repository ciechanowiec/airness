// The install-script record of the node project, against the resolved set its lockfile names. It is judged
// once, at the project root, where pnpm reads it for every member.
import path from 'node:path'
import { failed, type GateResult, passed } from './exec.js'
import { type InstallConfigInputs, layoutFindings } from './install-config.js'
import { installConfigOf } from './install-config-files.js'
import { installScriptFindings } from './install-policy.js'
import { hasInstallScript, type InstalledPackage, installedPackages } from './installed-packages.js'
import { LOCKFILE, type Lockfile, readLockfile } from './lockfile.js'
import { type Member, readText } from './project.js'
import { missingFindings, type ResolvedSet, resolvedSetOf } from './resolved-set.js'
import { ROOT_MEMBER_PATH } from './workspace.js'

/**
 * Read the resolved set of a project the gates judge from its store: refused when a setting moves the store,
 * the lockfile is unreadable or nothing is installed.
 * @param projectRoot the node project directory.
 * @param config the project's install configuration.
 * @returns the resolved set, or the findings that stop the gate.
 */
export const readResolvedSet = (projectRoot: string, config: InstallConfigInputs): ResolvedSet | readonly string[] => {
  const layout: readonly string[] = layoutFindings(config)
  if (layout.length > 0) {
    return layout
  }
  const text: string | undefined = readText(path.join(projectRoot, LOCKFILE))
  const lockfile: Lockfile | undefined = text === undefined ? undefined : readLockfile(text)
  const installed: readonly InstalledPackage[] | undefined = installedPackages(projectRoot)
  if (lockfile === undefined || installed === undefined) {
    return [
      `the gate reads ${LOCKFILE} and node_modules/.pnpm; airness:node-dependencies installs the locked ` +
        'dependencies before the gates',
    ]
  }
  return resolvedSetOf(lockfile, installed)
}

/** Every installed dependency that runs an install script is decided in allowBuilds, and only those are. */
export const installScripts = (member: Member): GateResult => {
  if (member.path !== ROOT_MEMBER_PATH) {
    return passed('the project root decides install scripts for every member')
  }
  const config: InstallConfigInputs = installConfigOf(member.projectRoot)
  const read: ResolvedSet | readonly string[] = readResolvedSet(member.projectRoot, config)
  if (!('installed' in read)) {
    return failed('the resolved set cannot be read', read)
  }
  const set: ResolvedSet = read
  const scripted: readonly InstalledPackage[] = set.installed.filter((entry: InstalledPackage): boolean =>
    hasInstallScript(entry),
  )
  const findings: readonly string[] = [
    ...missingFindings(set.missing),
    ...installScriptFindings({
      workspace: config.workspace,
      manifest: config.manifest,
      scripted,
      elsewhere: set.elsewhere,
    }),
  ]
  return findings.length > 0
    ? failed(`${String(findings.length)} install-script defect(s)`, findings)
    : passed(
        `allowBuilds decides each of the ${String(scripted.length)} dependencies that run an install script; ` +
          `${String(set.elsewhere.length)} package(s) limited to other systems are judged where they install`,
      )
}
