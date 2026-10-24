// The strict type check, against the project's own tsconfig.json, which only points at the shipped one.
import path from 'node:path'
import { failed, fromRun, type GateResult, runNode } from './exec.js'
import { type Member, readText, resolveTool } from './project.js'
import { tsconfigProblems } from './tsconfig-policy.js'

/** Strict type checking with the harness's TypeScript, once the tsconfig carries no relaxation. */
export const types = (member: Member): GateResult => {
  const problems: readonly string[] = tsconfigProblems(
    readText(path.join(member.root, 'tsconfig.json')),
    member.hasOwnRuntime,
    member.siblingPaths,
  )
  return problems.length > 0
    ? failed('the tsconfig.json relaxes the harness compiler options', problems)
    : fromRun(
        runNode(resolveTool('typescript', 'tsc'), ['--noEmit', '-p', 'tsconfig.json'], { cwd: member.root }),
        'the project type-checks under the harness compiler options',
        'the project does not type-check under the harness compiler options',
      )
}
