// Which dependencies may run an install script. pnpm installs with every script switched off, then runs the
// build scripts of the dependencies `allowBuilds` approves; this decides whether that record is complete
// and exact. Which packages a project trusts is the project's decision, so the rule asks only that every
// dependency with an install script is decided, and that every decision is about one.
import { isRecord } from './json-shapes.js'

/** The key of pnpm-workspace.yaml that records each install-script decision. */
const ALLOW_BUILDS: string = 'allowBuilds'

/** The other settings that decide install scripts, which would make allowBuilds no longer the whole record. */
const COMPETING_KEYS: readonly string[] = [
  'onlyBuiltDependencies',
  'onlyBuiltDependenciesFile',
  'neverBuiltDependencies',
  'ignoredBuiltDependencies',
  'dangerouslyAllowAllBuilds',
]

/** A package that runs an install script, by name and version. */
interface ScriptedPackage {
  readonly name: string
  readonly version: string
}

/** What the install-script rules read. */
export interface InstallScriptInputs {
  /** The parsed pnpm-workspace.yaml, or undefined when the project has none. */
  readonly workspace: unknown
  /** The project's parsed package.json. */
  readonly manifest: unknown
  /** Every installed package that runs an install script. */
  readonly scripted: readonly ScriptedPackage[]
  /**
   * Resolved packages limited to systems other than this host's, whose scripts this host cannot read. A
   * decision about one of them is kept, so the record that passes on one system passes on every system.
   */
  readonly elsewhere: readonly ScriptedPackage[]
}

const WORKSPACE_FILE: string = 'pnpm-workspace.yaml'

const isDecidedBy = (key: string, scripted: ScriptedPackage): boolean =>
  key === scripted.name || key === `${scripted.name}@${scripted.version}`

const competingFindings = (workspace: Record<string, unknown>, manifest: unknown): readonly string[] => {
  const pnpm: unknown = isRecord(manifest) ? manifest['pnpm'] : undefined
  return [
    ...COMPETING_KEYS.filter((key: string): boolean => Object.hasOwn(workspace, key)).map(
      (key: string): string =>
        `${WORKSPACE_FILE} ${key}: decides install scripts beside ${ALLOW_BUILDS}; remove it, ` +
        `${ALLOW_BUILDS} is the one record`,
    ),
    ...[ALLOW_BUILDS, ...COMPETING_KEYS]
      .filter((key: string): boolean => isRecord(pnpm) && Object.hasOwn(pnpm, key))
      .map(
        (key: string): string =>
          `package.json pnpm.${key}: install-script decisions belong in ${WORKSPACE_FILE} ${ALLOW_BUILDS}; remove it`,
      ),
  ]
}

const decisionFindings = (
  decisions: Record<string, unknown>,
  scripted: readonly ScriptedPackage[],
  elsewhere: readonly ScriptedPackage[],
): readonly string[] => [
  ...Object.entries(decisions)
    .filter(([, value]: readonly [string, unknown]): boolean => typeof value !== 'boolean')
    .map(([key]: readonly [string, unknown]): string => `${ALLOW_BUILDS}.${key} must be true or false`),
  ...scripted
    .filter((entry: ScriptedPackage): boolean =>
      Object.keys(decisions).every((key: string): boolean => !isDecidedBy(key, entry)),
    )
    .map(
      (entry: ScriptedPackage): string =>
        `${entry.name}@${entry.version} runs an install script that ${ALLOW_BUILDS} does not decide; ` +
        `add "${entry.name}: true" to run it, or "${entry.name}: false" to install it without running it`,
    ),
  ...Object.keys(decisions)
    .filter((key: string): boolean =>
      [...scripted, ...elsewhere].every((entry: ScriptedPackage): boolean => !isDecidedBy(key, entry)),
    )
    .map(
      (key: string): string =>
        `${ALLOW_BUILDS}.${key} names no installed dependency that runs an install script; remove it`,
    ),
]

/**
 * Every way the install-script record is missing, competed with, or out of step with what is installed.
 * @param inputs the workspace file, the manifest and the scripted packages.
 * @returns one finding per defect.
 */
export const installScriptFindings = (inputs: InstallScriptInputs): readonly string[] => {
  const workspace: Record<string, unknown> = isRecord(inputs.workspace) ? inputs.workspace : {}
  const decisions: unknown = workspace[ALLOW_BUILDS]
  return [
    ...(isRecord(decisions)
      ? decisionFindings(decisions, inputs.scripted, inputs.elsewhere)
      : [
          `${WORKSPACE_FILE} declares no ${ALLOW_BUILDS} mapping; decide every dependency that runs an install ` +
            `script there, as true or false. "${ALLOW_BUILDS}: {}" is the answer when none does`,
        ]),
    ...competingFindings(workspace, inputs.manifest),
  ]
}
