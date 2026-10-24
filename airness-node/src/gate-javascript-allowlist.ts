// No hand-written JavaScript: a JavaScript file is allowed only where a tool forces it and the project's
// allowlist names the role, with the engine that runs it.
import { existsSync, statSync } from 'node:fs'
import path from 'node:path'
import { failed, type GateResult, passed } from './exec.js'
import { hasExtension, JAVASCRIPT_EXTENSIONS, matchesRole } from './file-roles.js'
import type { Member } from './project.js'
import { type DeclaredExclusion, findConvenienceExclusions, findUndeclaredRuntimes } from './settings.js'
import { memberFiles } from './source-selection.js'
import { workingTreeFiles } from './working-tree.js'

// An enumerated path is not always a regular file: `git ls-files` reports a symlink and a submodule
// gitlink too.
const isRegularFile = (root: string, file: string): boolean => {
  const full: string = path.join(root, file)
  return existsSync(full) && statSync(full).isFile()
}

const allowlistEntries = (member: Member): readonly DeclaredExclusion[] =>
  member.settings.declaredExclusions.filter(
    (entry: DeclaredExclusion): boolean => entry.setting === 'javascriptAllowlist',
  )

/** Every JavaScript file the member carries is allowlisted by role, and every allowlist entry states one. */
export const javascriptAllowlist = (member: Member): GateResult => {
  const files: readonly string[] = memberFiles(workingTreeFiles(member.root), member.siblingPaths).filter(
    (file: string): boolean => isRegularFile(member.root, file),
  )
  const findings: readonly string[] = [
    ...findConvenienceExclusions(allowlistEntries(member)),
    ...findUndeclaredRuntimes(allowlistEntries(member)),
    ...files
      .filter(
        (file: string): boolean =>
          hasExtension(file, JAVASCRIPT_EXTENSIONS) && !matchesRole(file, member.settings.javascriptAllowlist),
      )
      .map((file: string): string => `${file} hand-written JavaScript is banned; write TypeScript instead`),
  ]
  return findings.length > 0
    ? failed(`${String(findings.length)} JavaScript allowlist violation(s)`, findings)
    : passed(`${String(files.length)} file(s) hold no hand-written JavaScript outside the allowlist`)
}
