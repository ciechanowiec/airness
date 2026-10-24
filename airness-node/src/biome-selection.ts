import { JAVASCRIPT_EXTENSIONS, matchesGlob } from './file-roles.js'
import { PROJECT_SETUP_FILE } from './settings.js'
import { memberFiles } from './source-selection.js'

const DIRECTORY_CONTENTS: string = '/**'

// Biome excludes directories by their path; its formatter removes a trailing recursive wildcard.
const biomeGeneratedExclusion = (artefact: string): string =>
  `!${artefact.endsWith(DIRECTORY_CONTENTS) ? artefact.slice(0, -DIRECTORY_CONTENTS.length) : artefact}`

/**
 * The ordered include and exclude patterns that decide which files of a member Biome judges.
 *
 * Selection does not live in the shipped Biome configuration, because Biome resolves a relative glob
 * against the directory of the configuration that declares it, and the shipped one sits inside
 * node_modules. The gate selects the files itself and hands Biome the list.
 * @param sourceRoots the directories holding first-party source.
 * @param generatedArtefacts the paths a generator owns, which Biome must not judge.
 * @returns the patterns, inclusions before exclusions.
 */
export const biomeIncludes = (
  sourceRoots: readonly string[],
  generatedArtefacts: readonly string[],
): readonly string[] => [
  ...sourceRoots.map((root: string): string => (root === '.' ? '**/*' : `${root}/**/*`)),
  'package.json',
  'biome.json',
  'tsconfig.json',
  '*.config.ts',
  '*.config.mts',
  '*.config.cts',
  '*.config.js',
  '*.config.jsx',
  '*.config.mjs',
  '*.config.cjs',
  PROJECT_SETUP_FILE,
  ...JAVASCRIPT_EXTENSIONS.map((extension: string): string => `**/*${extension}`),
  ...generatedArtefacts.map((artefact: string): string => biomeGeneratedExclusion(artefact)),
  '!**/*.scss',
  '!**/.next',
  '!**/node_modules',
]

/** Select owned files from the ordered include and exclude patterns. */
export const biomeSelection = (
  files: readonly string[],
  includes: readonly string[],
  nestedMembers: readonly string[],
): readonly string[] =>
  memberFiles(files, nestedMembers).filter((file: string): boolean => {
    const positive: readonly string[] = includes.filter((pattern: string): boolean => !pattern.startsWith('!'))
    const negative: readonly string[] = includes
      .filter((pattern: string): boolean => pattern.startsWith('!'))
      .map((pattern: string): string => pattern.slice(1))
    return (
      positive.some((pattern: string): boolean => matchesGlob(pattern, file)) &&
      !negative.some((pattern: string): boolean => matchesGlob(pattern, file) || matchesGlob(`${pattern}/**`, file))
    )
  })

/** A Biome configuration file, dotted or not, which the gate supplies itself and a project may keep only at its root. */
export const isNestedBiomeConfig = (file: string): boolean => /\/\.?biome\.jsonc?$/u.test(file)
