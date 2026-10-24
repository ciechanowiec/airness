// The type-aware lint layer, under the shipped ESLint configuration rather than any file the project keeps.
import path from 'node:path'
import { fromRun, type GateResult, runNode } from './exec.js'
import { harnessDirectory, type Member, resolveTool } from './project.js'
import { eslintArguments } from './workspace.js'

/**
 * ESLint with the harness configuration. `--max-warnings=0` is the backstop behind the severity
 * escalation in `eslint-core.ts`: it fails the gate on a warning whatever its source.
 */
export const eslint = (member: Member): GateResult => {
  const config: string = path.join(harnessDirectory(), 'dist', 'eslint.js')
  return fromRun(
    runNode(resolveTool('eslint'), [...eslintArguments(config, member.siblingPaths)], { cwd: member.root }),
    'ESLint reports no defect',
    'ESLint reports at least one defect',
  )
}
