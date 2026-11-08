// The type-aware lint layer, under the shipped ESLint configuration rather than any file the project keeps:
// the node layer's, with the blocks a declared stack layer adds composed after it by the harness.
import path from 'node:path'
import { fromRun, runNode } from './exec.js'
import type { GateResult } from './gate-result.js'
import { harnessDirectory, type Member, resolveTool } from './project.js'
import { ignoredWorkingTreePaths } from './working-tree.js'
import { eslintArguments } from './workspace.js'

/**
 * The shipped configuration, `eslint-layered`, which refuses, and so fails the gate, a stack layer block that
 * would weaken the node layer's verdict. The project names neither it nor the layer's blocks.
 * @returns the absolute path of the configuration.
 */
const shippedConfig = (): string => path.join(harnessDirectory(), 'dist', 'eslint-layered.js')

/**
 * ESLint with the given configuration, over what a commit would carry: every path Git ignores, such as a static
 * site an auxiliary server built, is passed over. `--max-warnings=0` is the backstop behind the severity escalation in
 * `eslint-core.ts`: it fails the gate on a warning whatever its source.
 */
const eslintRun = (member: Member, mode: 'check' | 'write', config: string): GateResult => {
  const target: readonly string[] = eslintArguments(config, member.siblingPaths, ignoredWorkingTreePaths(member.root))
  return fromRun(
    runNode(resolveTool('eslint'), [...target, ...(mode === 'write' ? ['--fix'] : [])], { cwd: member.root }),
    'ESLint reports no defect',
    'ESLint reports at least one defect',
  )
}

/**
 * Judge the member with the harness-owned layered configuration.
 * @param member the member to judge.
 * @param config the configuration ESLint runs, the shipped one unless a caller names another.
 * @returns the verdict.
 */
export const eslint = (member: Member, config: string = shippedConfig()): GateResult =>
  eslintRun(member, 'check', config)

/** Apply fixable lint rules within the gate's own member and generated-file boundaries. */
export const eslintWrite = (member: Member): GateResult => eslintRun(member, 'write', shippedConfig())
