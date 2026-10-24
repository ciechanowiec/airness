import { hasExtension, matchesGlob } from './file-roles.js'
import { jsxAccessibilityFiles } from './jsx-accessibility-scope.js'
import { type OxlintRule, oxlintRules } from './oxlint-policy.js'
import { analysisBoundaries } from './workspace.js'

/** Authored JavaScript and TypeScript, including configuration and hidden tooling. */
export const OXLINT_EXTENSIONS: readonly string[] = ['.ts', '.tsx', '.mts', '.cts', '.js', '.jsx', '.mjs', '.cjs']

const OUTPUTS: readonly string[] = ['**/node_modules/**', '.next/**', 'dist/**', 'build/**', 'out/**', 'coverage/**']

/** Select committable source without giving generated output or siblings a second owner. */
export const oxlintSourceFiles = (
  files: readonly string[],
  generated: readonly string[],
  siblings: readonly string[],
): readonly string[] => {
  const ignored: readonly string[] = [...OUTPUTS, ...generated, ...analysisBoundaries(siblings)]
  return [...new Set(files)].filter(
    (file: string): boolean =>
      hasExtension(file, OXLINT_EXTENSIONS) &&
      !/\.d\.(?:ts|mts|cts)$/u.test(file) &&
      !ignored.some((pattern: string): boolean => matchesGlob(pattern, file)),
  )
}

/** One native invocation, whose report must account for exactly these files and rules. */
export interface OxlintGroup {
  readonly files: readonly string[]
  readonly rules: readonly OxlintRule[]
}

/** Partition source by analyzer ownership: JSX in an application is also held to the accessibility rules. */
export const oxlintGroups = (files: readonly string[], hasAppRuntime: boolean): readonly OxlintGroup[] => {
  const jsx: ReadonlySet<string> = new Set(hasAppRuntime ? jsxAccessibilityFiles(files, [], []) : [])
  return [
    { files: files.filter((file: string): boolean => !jsx.has(file)), rules: oxlintRules(false) },
    { files: files.filter((file: string): boolean => jsx.has(file)), rules: oxlintRules(true) },
  ].filter((group: OxlintGroup): boolean => group.files.length > 0)
}
