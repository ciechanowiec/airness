// The language source rules, applied to every TypeScript file under the member's source roots.
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { failed, type GateResult, passed } from './exec.js'
import { hasExtension, TYPESCRIPT_SOURCE_EXTENSIONS } from './file-roles.js'
import type { Member } from './project.js'
import { findSourceViolations } from './source-rules.js'
import { memberFiles } from './source-selection.js'
import type { SourceViolation } from './source-violation.js'
import { workingTreeFiles } from './working-tree.js'

const candidatesOf = (member: Member): readonly string[] =>
  memberFiles(workingTreeFiles(member.root), member.siblingPaths).filter(
    (file: string): boolean =>
      hasExtension(file, TYPESCRIPT_SOURCE_EXTENSIONS) &&
      member.settings.sourceRoots.some((root: string): boolean => file.startsWith(`${root}/`)) &&
      existsSync(path.join(member.root, file)),
  )

/** Apply the source rules to every TypeScript file under the declared source roots. */
export const sourceRules = (member: Member): GateResult => {
  const candidates: readonly string[] = candidatesOf(member)
  const findings: readonly string[] = candidates.flatMap((file: string): readonly string[] =>
    findSourceViolations(readFileSync(path.join(member.root, file), 'utf8'), file).map(
      (violation: SourceViolation): string =>
        `${file}:${String(violation.line)} [${violation.rule}] ${violation.reason}`,
    ),
  )
  return findings.length > 0
    ? failed(`${String(findings.length)} source rule violation(s)`, findings)
    : passed(`${String(candidates.length)} source file(s) follow the source rules`)
}
