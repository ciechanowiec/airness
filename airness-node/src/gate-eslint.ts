// The type-aware lint layer, under the shipped ESLint configuration rather than any file the project keeps:
// the node layer's, with the blocks a declared stack layer adds composed after it by the harness.
import path from 'node:path'
import { fromRun, type GateResult, runNode } from './exec.js'
import { harnessDirectory, type Member, resolveTool } from './project.js'
import { eslintArguments } from './workspace.js'

/**
 * ESLint with the harness configuration. `--max-warnings=0` is the backstop behind the severity
 * escalation in `eslint-core.ts`: it fails the gate on a warning whatever its source. The configuration is
 * `eslint-layered`, which refuses, and so fails the gate, a stack layer block that would weaken the node
 * layer's verdict. The project names neither it nor the layer's blocks.
 */
const eslintRun = (member: Member, mode: 'check' | 'write'): GateResult => {
  const config: string = path.join(harnessDirectory(), 'dist', 'eslint-layered.js')
  return fromRun(
    runNode(
      resolveTool('eslint'),
      [...eslintArguments(config, member.siblingPaths), ...(mode === 'write' ? ['--fix'] : [])],
      { cwd: member.root },
    ),
    'ESLint reports no defect',
    'ESLint reports at least one defect',
  )
}

/** Judge the member with the harness-owned layered configuration. */
export const eslint = (member: Member): GateResult => eslintRun(member, 'check')

/** Apply fixable lint rules within the gate's own member and generated-file boundaries. */
export const eslintWrite = (member: Member): GateResult => eslintRun(member, 'write')
