// The install-time settings a project could use to change what a resolved name and version contain, or
// where pnpm puts what it installs. The first kind would let a project keep the harness version and replace
// what it holds; the second would move the store the gates read the installed packages from.
import { asRecord, isRecord } from './json-shapes.js'
import type { NpmConfig } from './npmrc.js'

/** What the install-config rules read at the project root. */
export interface InstallConfigInputs {
  /** The parsed pnpm-workspace.yaml, or undefined when the project has none. */
  readonly workspace: unknown
  /** The project's parsed package.json. */
  readonly manifest: unknown
  /** The npm configuration pnpm reads for the project. */
  readonly npmrc: NpmConfig
  /** The names of the files at the project root. */
  readonly files: readonly string[]
}

const WORKSPACE_FILE: string = 'pnpm-workspace.yaml'

const WORKSPACE_REWRITES: readonly string[] = [
  'overrides',
  'patchedDependencies',
  'packageExtensions',
  'pnpmfile',
  'globalPnpmfile',
]
const MANIFEST_REWRITES: readonly string[] = ['overrides', 'resolutions']
const MANIFEST_PNPM_REWRITES: readonly string[] = ['overrides', 'patchedDependencies', 'packageExtensions']
const NPMRC_REWRITES: readonly string[] = ['pnpmfile', 'global-pnpmfile']
const PNPMFILES: readonly string[] = ['.pnpmfile.cjs', '.pnpmfile.mjs']

const REWRITE_REPAIR: string =
  'it changes what a resolved package contains, so the harness could keep its version and run other code; remove it'

const present = (record: unknown, keys: readonly string[]): readonly string[] =>
  keys.filter((key: string): boolean => isRecord(record) && Object.hasOwn(record, key))

/**
 * Every install-time rewrite the project configures: overrides, patches, package extensions and pnpmfile hooks.
 * @param inputs the project's configuration.
 * @returns one finding per rewrite.
 */
export const rewriteFindings = (inputs: InstallConfigInputs): readonly string[] => [
  ...present(inputs.workspace, WORKSPACE_REWRITES).map(
    (key: string): string => `${WORKSPACE_FILE} ${key}: ${REWRITE_REPAIR}`,
  ),
  ...present(inputs.manifest, MANIFEST_REWRITES).map((key: string): string => `package.json ${key}: ${REWRITE_REPAIR}`),
  ...present(asRecord(inputs.manifest)['pnpm'], MANIFEST_PNPM_REWRITES).map(
    (key: string): string => `package.json pnpm.${key}: ${REWRITE_REPAIR}`,
  ),
  ...NPMRC_REWRITES.filter((key: string): boolean => inputs.npmrc.has(key)).map(
    (key: string): string => `.npmrc ${key}: ${REWRITE_REPAIR}`,
  ),
  ...PNPMFILES.filter((file: string): boolean => inputs.files.includes(file)).map(
    (file: string): string => `${file}: a pnpmfile hook ${REWRITE_REPAIR}`,
  ),
]

// Each setting that moves or reshapes the virtual store, and the value that leaves it where it belongs.
const LAYOUT: readonly { readonly workspace: string; readonly npmrc: string; readonly allowed?: string | boolean }[] = [
  { workspace: 'nodeLinker', npmrc: 'node-linker', allowed: 'isolated' },
  { workspace: 'virtualStoreDir', npmrc: 'virtual-store-dir' },
  { workspace: 'modulesDir', npmrc: 'modules-dir' },
  { workspace: 'enableGlobalVirtualStore', npmrc: 'enable-global-virtual-store', allowed: false },
]

// A setting compared as it reads in either file: `false` in YAML and `false` in an `.npmrc` are one value.
const settingText = (value: unknown): string =>
  typeof value === 'string' || typeof value === 'boolean' ? String(value) : JSON.stringify(value)

const isAllowed = (value: unknown, allowed: string | boolean | undefined): boolean =>
  allowed !== undefined && settingText(value) === String(allowed)

/**
 * Every setting that moves the virtual store the gates read installed packages from, or replaces it.
 * @param inputs the project's configuration.
 * @returns one finding per setting.
 */
export const layoutFindings = (inputs: InstallConfigInputs): readonly string[] =>
  LAYOUT.flatMap((setting): readonly string[] => {
    const workspace: unknown = asRecord(inputs.workspace)[setting.workspace]
    const npmrc: string | undefined = inputs.npmrc.get(setting.npmrc)
    return [
      ...(workspace === undefined || isAllowed(workspace, setting.allowed)
        ? []
        : [`${WORKSPACE_FILE} ${setting.workspace}: moves the store the gates read; remove it`]),
      ...(npmrc === undefined || isAllowed(npmrc, setting.allowed)
        ? []
        : [`.npmrc ${setting.npmrc}: moves the store the gates read; remove it`]),
    ]
  })
