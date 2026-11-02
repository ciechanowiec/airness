// The strict type check, against the project's own tsconfig.json, which only points at the shipped one.
import { existsSync } from 'node:fs'
import path from 'node:path'
import { failed, fromRun, type GateResult, runNode } from './exec.js'
import { type Member, readText, resolveTool } from './project.js'
import { tsconfigProblems } from './tsconfig-policy.js'

// The compiler's incremental cache, kept below the member's node_modules where neither git nor a pack reaches
// it. Left to the compiler it lands beside tsconfig.json, or inside outDir when the project declares one, and
// a library that publishes its outDir would ship the cache: a file that changes whenever any checked file
// does, specs included, although no shipped output did, so a rebuild packs other bytes and stales every
// consumer lock. The cache stays incremental; only its place is the harness's.
const BUILD_INFO: string = path.join('node_modules', '.airness', 'types.tsbuildinfo')

/** Strict type checking with the harness's TypeScript, once the tsconfig carries no relaxation. */
export const types = (member: Member): GateResult => {
  const problems: readonly string[] = tsconfigProblems(
    readText(path.join(member.root, 'tsconfig.json')),
    member.hasOwnRuntime,
    member.siblingPaths,
    existsSync(path.join(member.root, '.storybook')),
  )
  return problems.length > 0
    ? failed('the tsconfig.json relaxes the harness compiler options', problems)
    : fromRun(
        runNode(
          resolveTool('typescript', 'tsc'),
          ['--noEmit', '-p', 'tsconfig.json', '--tsBuildInfoFile', BUILD_INFO],
          { cwd: member.root },
        ),
        'the project type-checks under the harness compiler options',
        'the project does not type-check under the harness compiler options',
      )
}
