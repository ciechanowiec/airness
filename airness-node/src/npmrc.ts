// The npm configuration pnpm reads for a node project: the user's `.npmrc`, the project's, and the
// environment, later sources winning. The gates read it for the registry each package comes from and for the
// settings that change what an install does.
//
// The environment is read as pnpm 12 reads it, which is not as npm does: a setting comes from a
// `pnpm_config_` variable, an underscore in its name read as a dash, and no longer from an `npm_config_` one,
// so `npm_config_registry` changes nothing pnpm does. A registry credential (`//host/:_authToken`) is read
// from either prefix, a scope's registry from neither, and the user file is named by `pnpm_config_userconfig`,
// else `npm_config_userconfig`. Reading what pnpm ignores would judge a registry pnpm never asked.
import { homedir } from 'node:os'
import path from 'node:path'
import { readText } from './project.js'

/** The registry pnpm uses when nothing names another. */
export const DEFAULT_REGISTRY: string = 'https://registry.npmjs.org/'

const PNPM_PREFIX: string = 'pnpm_config_'
const NPM_PREFIX: string = 'npm_config_'
const CREDENTIAL_PREFIX: string = '//'
const SCOPE_PREFIX: string = '@'
const COMMENT: RegExp = /^\s*[#;]/u
const VARIABLE: RegExp = /\$\{([^}]+)\}/gu

/** The configuration, every key as written in an `.npmrc`. */
export type NpmConfig = ReadonlyMap<string, string>

const expand = (value: string, environment: NodeJS.ProcessEnv): string =>
  value.replaceAll(VARIABLE, (_: string, name: string): string => environment[name] ?? '')

/**
 * Read the lines of one `.npmrc`.
 * @param text the file's text.
 * @param environment the variables `${NAME}` expands from.
 * @returns each key with its value.
 */
export const parseNpmrc = (text: string, environment: NodeJS.ProcessEnv): NpmConfig =>
  new Map(
    text
      .split(/\r?\n/u)
      .filter((line: string): boolean => !COMMENT.test(line) && line.includes('='))
      .map((line: string): readonly [string, string] => {
        const at: number = line.indexOf('=')
        return [line.slice(0, at).trim(), expand(line.slice(at + 1).trim(), environment)]
      }),
  )

// Every variable whose name starts with a prefix, in any case, with the prefix removed.
const variablesWith = (environment: NodeJS.ProcessEnv, prefix: string): readonly (readonly [string, string])[] =>
  Object.entries(environment)
    .filter(
      ([name, value]: readonly [string, string | undefined]): boolean =>
        name.toLowerCase().startsWith(prefix) && value !== undefined,
    )
    .map(([name, value]: readonly [string, string | undefined]): readonly [string, string] => [
      name.slice(prefix.length),
      String(value),
    ])

const USER_CONFIG: string = 'userconfig'

const userConfigWith = (environment: NodeJS.ProcessEnv, prefix: string): string | undefined =>
  variablesWith(environment, prefix).find(
    ([name]: readonly [string, string]): boolean => name.toLowerCase() === USER_CONFIG,
  )?.[1]

// A setting's name as an `.npmrc` writes it. A credential's key names a host and a path, which keep their case.
const settingKey = (name: string): string =>
  name.startsWith(CREDENTIAL_PREFIX) ? name : name.toLowerCase().replaceAll('_', '-')

// The settings pnpm reads from the environment: every `pnpm_config_` setting but a scope's registry, and the
// registry credentials of `npm_config_` variables, a `pnpm_config_` one winning.
const environmentSettings = (environment: NodeJS.ProcessEnv): readonly (readonly [string, string])[] => [
  ...variablesWith(environment, NPM_PREFIX).filter(([name]: readonly [string, string]): boolean =>
    name.startsWith(CREDENTIAL_PREFIX),
  ),
  ...variablesWith(environment, PNPM_PREFIX)
    .filter(([name]: readonly [string, string]): boolean => !name.startsWith(SCOPE_PREFIX))
    .map(([name, value]: readonly [string, string]): readonly [string, string] => [settingKey(name), value]),
]

/**
 * The configuration pnpm reads for a project: the user file, the project file, then the environment.
 * @param projectRoot the node project directory.
 * @param environment the process environment.
 * @returns the merged configuration.
 */
export const npmConfigOf = (projectRoot: string, environment: NodeJS.ProcessEnv = process.env): NpmConfig => {
  const userFile: string =
    userConfigWith(environment, PNPM_PREFIX) ??
    userConfigWith(environment, NPM_PREFIX) ??
    path.join(homedir(), '.npmrc')
  const user: string = readText(userFile) ?? ''
  const project: string = readText(path.join(projectRoot, '.npmrc')) ?? ''
  return new Map([
    ...parseNpmrc(user, environment),
    ...parseNpmrc(project, environment),
    ...environmentSettings(environment),
  ])
}

const withSlash = (url: string): string => (url.endsWith('/') ? url : `${url}/`)

/**
 * The registry pnpm fetches a package from: its scope's registry, else the configured one, else npmjs.
 * @param config the configuration.
 * @param name the package name.
 * @returns the registry URL, ending in a slash.
 */
export const registryFor = (config: NpmConfig, name: string): string => {
  const scope: string | undefined = name.startsWith('@') ? name.slice(0, name.indexOf('/')) : undefined
  const scoped: string | undefined = scope === undefined ? undefined : config.get(`${scope}:registry`)
  return withSlash(scoped ?? config.get('registry') ?? DEFAULT_REGISTRY)
}

const TOKEN_SUFFIX: string = ':_authToken'

/** Where a credential applies: a host, with its port when it names one, and a path below it. */
interface CredentialScope {
  readonly host: string
  readonly segments: readonly string[]
}

const segmentsOf = (pathname: string): readonly string[] =>
  pathname.split('/').filter((segment: string): boolean => segment.length > 0)

// A key is nerf-darted: `//host[:port]/path/:_authToken`, the scheme left out. Anything else names no scope.
const scopeOfKey = (key: string): CredentialScope | undefined => {
  if (!(key.startsWith('//') && key.endsWith(TOKEN_SUFFIX))) {
    return undefined
  }
  const location: string = key.slice('//'.length, -TOKEN_SUFFIX.length)
  const slash: number = location.indexOf('/')
  const host: string = (slash === -1 ? location : location.slice(0, slash)).toLowerCase()
  return host.length > 0 ? { host, segments: segmentsOf(slash === -1 ? '' : location.slice(slash)) } : undefined
}

const isCovered = (scope: CredentialScope, host: string, segments: readonly string[]): boolean =>
  scope.host === host && scope.segments.every((segment: string, index: number): boolean => segments[index] === segment)

/**
 * The token configured for a registry: the `//host/path/:_authToken` whose host and port equal the registry's
 * exactly and whose path is the longest run of whole leading segments of the registry's path. No token is
 * sent to a registry reached over plain HTTP, where anyone on the way could read it.
 * @param config the configuration.
 * @param registry the registry URL.
 * @returns the token, or undefined when none applies.
 */
export const tokenFor = (config: NpmConfig, registry: string): string | undefined => {
  const url: URL | undefined = URL.canParse(registry) ? new URL(registry) : undefined
  if (url?.protocol !== 'https:') {
    return undefined
  }
  const segments: readonly string[] = segmentsOf(url.pathname)
  return [...config]
    .flatMap(([key, value]: readonly [string, string]): readonly (readonly [CredentialScope, string])[] => {
      const scope: CredentialScope | undefined = scopeOfKey(key)
      return scope !== undefined && isCovered(scope, url.host, segments) ? [[scope, value]] : []
    })
    .toSorted(
      (left: readonly [CredentialScope, string], right: readonly [CredentialScope, string]): number =>
        right[0].segments.length - left[0].segments.length,
    )
    .map(([, value]: readonly [CredentialScope, string]): string => value)[0]
}

/** What the lockfile records of where a package comes from. */
export interface RegistryResolution {
  readonly name: string
  readonly source: string
  readonly tarball: string
}

/**
 * Whether a package resolves from the registry configured for it: by name, or as a tarball below that
 * registry's URL, which pnpm records for a registry other than the default one.
 * @param config the configuration.
 * @param resolved how the lockfile resolves the package.
 * @returns true when a registry serves the package.
 */
export const isFromItsRegistry = (config: NpmConfig, resolved: RegistryResolution): boolean =>
  resolved.source === 'registry' ||
  (resolved.source === 'tarball' && resolved.tarball.startsWith(registryFor(config, resolved.name)))
