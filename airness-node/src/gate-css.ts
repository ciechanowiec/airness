// The stylesheet facet: every authored stylesheet passes Stylelint under the shipped configuration, and every
// SCSS file is formatted as the shipped Prettier options format it. A member without a stylesheet has no
// facet to judge. The project cannot keep its own Stylelint configuration or ignore file, because either
// would decide which rules or files the verdict reads.
import { readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fromRun, type RunResult, runNode } from './exec.js'
import { hasExtension, isWithinSourceRoots, STYLESHEET_EXTENSIONS } from './file-roles.js'
import { failed, type GateResult, notApplicable, passed } from './gate-result.js'
import { harnessDirectory, type Member, resolveTool } from './project.js'
import { matchesAnyGlob, memberFiles } from './source-selection.js'
import { presentWorkingTreeFiles } from './working-tree.js'

// Every name Stylelint's own lookup reads a configuration or an ignore list from.
const PROJECT_CONFIGURATION: RegExp =
  /(?:^|\/)(?:\.stylelintrc(?:\.[^/]*)?|stylelint\.config\.[^/]+|\.stylelintignore)$/u

const STYLELINT_CONFIG = (): string => path.join(harnessDirectory(), 'configs', 'stylelint.json')

const isUnderSourceRoot = (member: Member, file: string): boolean =>
  isWithinSourceRoots(file, member.settings.sourceRoots)

// The authored stylesheets of a member: under a source root, outside every nested member, not generated.
const stylesheetFiles = (member: Member, files: readonly string[]): readonly string[] =>
  memberFiles(files, member.siblingPaths).filter(
    (file: string): boolean =>
      hasExtension(file, STYLESHEET_EXTENSIONS) &&
      isUnderSourceRoot(member, file) &&
      !matchesAnyGlob(file, member.settings.generatedArtefacts),
  )

const formatScss = (member: Member, text: string): RunResult =>
  runNode(
    resolveTool('prettier'),
    [
      '--parser',
      'scss',
      '--config',
      path.join(harnessDirectory(), 'configs', 'prettier-scss.json'),
      '--no-editorconfig',
    ],
    { cwd: member.root, input: text },
  )

const scssFindings = (member: Member, files: readonly string[], mode: 'check' | 'write'): readonly string[] =>
  files
    .filter((file: string): boolean => file.endsWith('.scss'))
    .flatMap((file: string): readonly string[] => {
      const text: string = readFileSync(path.join(member.root, file), 'utf8')
      const formatted: RunResult = formatScss(member, text)
      if (formatted.code !== 0) {
        return [`${file}: SCSS formatting failed: ${formatted.output}`]
      }
      if (mode === 'write') {
        writeFileSync(path.join(member.root, file), formatted.stdout)
        return []
      }
      return formatted.stdout === text ? [] : [`${file}: SCSS formatting differs from the shipped Prettier options`]
    })

/** Judge every authored stylesheet with Stylelint, and every SCSS file's formatting. */
const cssRun = (member: Member, mode: 'check' | 'write'): GateResult => {
  const tracked: readonly string[] = presentWorkingTreeFiles(member.root)
  const owned: readonly string[] = memberFiles(tracked, member.siblingPaths).filter((file: string): boolean =>
    PROJECT_CONFIGURATION.test(file),
  )
  if (owned.length > 0) {
    return failed(
      'a Stylelint configuration is owned by Airness',
      owned.map((file: string): string => `${file}: delete it; the css gate runs the shipped configuration`),
    )
  }
  const files: readonly string[] = stylesheetFiles(member, tracked)
  if (files.length === 0) {
    return notApplicable('the member has no authored stylesheet')
  }
  const lint: GateResult = fromRun(
    runNode(
      resolveTool('stylelint'),
      ['--config', STYLELINT_CONFIG(), '--max-warnings=0', ...(mode === 'write' ? ['--fix'] : []), ...files],
      {
        cwd: member.root,
      },
    ),
    'stylesheets pass Stylelint',
    'a stylesheet does not pass Stylelint',
  )
  if (!lint.ok) {
    return lint
  }
  const findings: readonly string[] = scssFindings(member, files, mode)
  return findings.length === 0
    ? passed(`${String(files.length)} stylesheet(s) pass Stylelint and formatting`)
    : failed('SCSS formatting differs', findings)
}

/** Check every authored stylesheet without writing to the project. */
export const css = (member: Member): GateResult => cssRun(member, 'check')

/** Fix and format the same authored stylesheets the CSS gate reads. */
export const cssWrite = (member: Member): GateResult => cssRun(member, 'write')
