// The npm configuration pnpm reads for a node project: the user's `.npmrc`, the project's, and the
// `npm_config_` variables, later sources winning. The gates read it for the registry each package comes
// from and for the settings that change what an install does.
import { homedir } from 'node:os'
import path from 'node:path'
import { readText } from './project.js'

/** The registry pnpm uses when nothing names another. */
export const DEFAULT_REGISTRY: string = 'https://registry.npmjs.org/'

const ENVIRONMENT_PREFIX: string = 'npm_config_'
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

/**
 * The configuration pnpm reads for a project: the user file, the project file, then the environment.
 * @param projectRoot the node project directory.
 * @param environment the process environment.
 * @returns the merged configuration.
 */
export const npmConfigOf = (projectRoot: string, environment: NodeJS.ProcessEnv = process.env): NpmConfig => {
  const userFile: string = environment['npm_config_userconfig'] ?? path.join(homedir(), '.npmrc')
  const fromEnvironment: readonly (readonly [string, string])[] = Object.entries(environment)
    .filter(([name]: readonly [string, string | undefined]): boolean =>
      name.toLowerCase().startsWith(ENVIRONMENT_PREFIX),
    )
    .map(([name, value]: readonly [string, string | undefined]): readonly [string, string] => [
      name.slice(ENVIRONMENT_PREFIX.length),
      String(value),
    ])
  const user: string = readText(userFile) ?? ''
  const project: string = readText(path.join(projectRoot, '.npmrc')) ?? ''
  return new Map([...parseNpmrc(user, environment), ...parseNpmrc(project, environment), ...fromEnvironment])
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

/**
 * The token configured for a registry, matched on the longest `//host/path/:_authToken` prefix of its URL.
 * @param config the configuration.
 * @param registry the registry URL.
 * @returns the token, or undefined when none is configured.
 */
export const tokenFor = (config: NpmConfig, registry: string): string | undefined => {
  const location: string = registry.replace(/^https?:/u, '')
  return [...config]
    .filter(([key]: readonly [string, string]): boolean => key.endsWith(':_authToken'))
    .map(([key, value]: readonly [string, string]): readonly [string, string] => [
      key.slice(0, -':_authToken'.length),
      value,
    ])
    .filter(([prefix]: readonly [string, string]): boolean => location.startsWith(prefix))
    .toSorted(
      (left: readonly [string, string], right: readonly [string, string]): number => right[0].length - left[0].length,
    )
    .map(([, value]: readonly [string, string]): string => value)[0]
}
