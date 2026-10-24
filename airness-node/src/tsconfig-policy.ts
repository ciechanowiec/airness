// What a node project's tsconfig.json may say. The strictness lives in the shipped configuration, and the
// project's file only points at it, so a project cannot quietly weaken type checking: the extends is fixed,
// the include and exclude are fixed, and only the compiler options that describe the project itself are its.
import { TYPESCRIPT_SOURCE_EXTENSIONS } from './file-roles.js'
import { asRecord, type ParsedJson, parseJsonc } from './json-shapes.js'

/**
 * The shipped configuration a project's tsconfig.json extends: the literal file path, which TypeScript and
 * Next.js both resolve, rather than the bare subpath Next.js does not read.
 */
export const REQUIRED_TSCONFIG_EXTENDS: string = '@airness/node/tsconfig.json'

// The compiler options a project may set for itself. Every other one is a strictness decision the harness
// owns, so a local override is how a project would weaken type checking.
const ALLOWED_COMPILER_OPTIONS: ReadonlySet<string> = new Set([
  'paths',
  'baseUrl',
  'plugins',
  'types',
  'rootDir',
  'outDir',
])

// TypeScript resolves these globs against the directory of the configuration that declares them, so they
// sit in the project's own file while the harness decides their contents. `target` is the Maven build
// directory every node project sits beside or inside.
const LIBRARY_PATHS: Readonly<Record<string, readonly string[]>> = {
  include: TYPESCRIPT_SOURCE_EXTENSIONS.map((extension: string): string => `**/*${extension}`),
  exclude: ['node_modules', 'dist', 'target'],
}

const APPLICATION_PATHS: Readonly<Record<string, readonly string[]>> = {
  include: [
    'next-env.d.ts',
    ...TYPESCRIPT_SOURCE_EXTENSIONS.map((extension: string): string => `**/*${extension}`),
    '.next/types/**/*.ts',
    '.next/dev/types/**/*.ts',
  ],
  exclude: ['node_modules', 'target'],
}

/**
 * The include and exclude one member must carry. A recursive include at a workspace root would sweep every
 * nested member's sources into the root's project, under the root's `paths`, so each nested member is
 * excluded: it is compiled by its own run.
 * @param hasOwnRuntime whether the member is a Next application.
 * @param nestedMembers the members inside this one, relative to it.
 * @returns the required include and exclude.
 */
export const tsconfigPathsFor = (
  hasOwnRuntime: boolean,
  nestedMembers: readonly string[],
): Readonly<Record<string, readonly string[]>> => {
  const base: Readonly<Record<string, readonly string[]>> = hasOwnRuntime ? APPLICATION_PATHS : LIBRARY_PATHS
  return { ...base, exclude: [...(base['exclude'] ?? []), ...nestedMembers] }
}

const pathProblems = (
  parsed: Record<string, unknown>,
  required: Readonly<Record<string, readonly string[]>>,
): readonly string[] =>
  Object.entries(required)
    .filter(([key, value]: readonly [string, readonly string[]]): boolean => {
      return JSON.stringify(parsed[key]) !== JSON.stringify(value)
    })
    .map(
      ([key, value]: readonly [string, readonly string[]]): string =>
        `tsconfig.json ${key}: must be exactly ${JSON.stringify(value)}`,
    )

/**
 * Every way a project's tsconfig.json departs from what the harness requires of it.
 * @param config the file's text, or undefined when the project has none.
 * @param hasOwnRuntime whether the member is a Next application.
 * @param nestedMembers the members inside this one, relative to it.
 * @returns one finding per departure.
 */
export const tsconfigProblems = (
  config: string | undefined,
  hasOwnRuntime: boolean,
  nestedMembers: readonly string[],
): readonly string[] => {
  if (config === undefined) {
    return [`tsconfig.json is missing; write one that extends ${REQUIRED_TSCONFIG_EXTENDS}`]
  }
  const read: ParsedJson = parseJsonc(config)
  if (read.problem !== undefined) {
    return [`tsconfig.json is not valid JSON: ${read.problem}`]
  }
  const parsed: Record<string, unknown> = asRecord(read.value)
  return [
    ...(parsed['extends'] === REQUIRED_TSCONFIG_EXTENDS
      ? []
      : [`tsconfig.json must declare "extends": "${REQUIRED_TSCONFIG_EXTENDS}"`]),
    ...Object.keys(asRecord(parsed['compilerOptions']))
      .filter((key: string): boolean => !ALLOWED_COMPILER_OPTIONS.has(key))
      .map(
        (key: string): string =>
          `tsconfig.json compilerOptions.${key}: the harness owns this compiler option; remove the override`,
      ),
    ...pathProblems(parsed, tsconfigPathsFor(hasOwnRuntime, nestedMembers)),
  ]
}
