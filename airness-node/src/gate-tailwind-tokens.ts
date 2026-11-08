// The Tailwind facet: every visual value a Tailwind project renders comes from the theme. The rule lives in
// tailwind-tokens.ts; this file supplies which files carry Tailwind classes and what a finding reads like.
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { isTailwindProject } from './facets.js'
import { hasExtension, isWithinSourceRoots } from './file-roles.js'
import { failed, type GateResult, notApplicable, passed } from './gate-result.js'
import type { Member } from './project.js'
import { matchesAnyGlob, memberFiles } from './source-selection.js'
import { type ArbitraryValueViolation, findArbitraryValues } from './tailwind-tokens.js'
import { presentWorkingTreeFiles } from './working-tree.js'

// Scoped by role rather than by a list of folders. A Tailwind class is written in markup: JSX in a client-rendered
// project, and an HTML template, such as a Thymeleaf page, in a server-rendered one whose Tailwind build reads
// its templates. Those are where the rule can find a class, and markup alone is also the scope that keeps the
// rule from misreading a regex character class such as `[a-z]-[0-9]` in ordinary TypeScript.
const MARKUP_EXTENSIONS: readonly string[] = ['.tsx', '.jsx', '.html', '.htm']

const isScanned = (member: Member, file: string): boolean =>
  hasExtension(file, MARKUP_EXTENSIONS) &&
  isWithinSourceRoots(file, member.settings.sourceRoots) &&
  !matchesAnyGlob(file, member.settings.generatedArtefacts)

/** Every visual value in a Tailwind project's markup comes from the theme rather than an arbitrary literal. */
export const tailwindTokens = (member: Member): GateResult => {
  if (!isTailwindProject(member)) {
    return notApplicable('the member does not declare tailwindcss')
  }
  const scanned: readonly string[] = memberFiles(presentWorkingTreeFiles(member.root), member.siblingPaths).filter(
    (file: string): boolean => isScanned(member, file),
  )
  const findings: readonly string[] = scanned.flatMap((file: string): readonly string[] =>
    findArbitraryValues(readFileSync(path.join(member.root, file), 'utf8')).map(
      (violation: ArbitraryValueViolation): string =>
        `${file}:${String(violation.line)}:${String(violation.column)} ` +
        `arbitrary Tailwind value \`${violation.value}\`; apply a theme token through a utility class`,
    ),
  )
  return findings.length > 0
    ? failed(`${String(findings.length)} arbitrary Tailwind value(s)`, findings)
    : passed(`${String(scanned.length)} markup file(s) use only token-bound Tailwind utilities`)
}
