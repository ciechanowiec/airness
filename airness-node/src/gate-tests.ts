// The member's own suite, run by the member's own Vitest under the shipped configuration, with the per-file
// coverage floor. The runner is the member's copy because its specs import it, and a second copy would load
// a second module registry.
import { createRequire } from 'node:module'
import path from 'node:path'
import { coverageCandidates } from './coverage-policy.js'
import { failed, fromRun, type GateResult, passed, runNode } from './exec.js'
import { asRecord, asStringRecord, asText } from './json-shapes.js'
import { harnessDirectory, type Member, readJson } from './project.js'
import { presentWorkingTreeFiles } from './working-tree.js'

const VITEST: string = 'vitest'

// The member's Vitest entry point, resolved the way the member's own specs resolve `vitest`.
const memberVitest = (member: Member): string | undefined => {
  try {
    const manifest: string = createRequire(path.join(member.root, 'package.json')).resolve(`${VITEST}/package.json`)
    const bin: unknown = asRecord(readJson(manifest))['bin']
    return path.join(path.dirname(manifest), asText(asStringRecord(bin)[VITEST]))
  } catch {
    return undefined
  }
}

/** The member's suite passes, and every measured file meets the coverage floor. */
export const tests = (member: Member): GateResult => {
  if (coverageCandidates(presentWorkingTreeFiles(member.root), member.settings, member.siblingPaths).length === 0) {
    return passed('the member holds no source the floor measures, so there is no suite to run')
  }
  const vitest: string | undefined = memberVitest(member)
  if (vitest === undefined) {
    return failed('vitest could not be resolved from the member', [
      'declare vitest and @vitest/coverage-v8 as development dependencies; the member runs its own copy',
    ])
  }
  const config: string = path.join(harnessDirectory(), 'dist', 'vitest.js')
  return fromRun(
    runNode(vitest, ['run', '--config', config, '--coverage'], {
      cwd: member.root,
      env: { NODE_OPTIONS: '--no-deprecation' },
    }),
    'the suite passes and every measured file meets the coverage floor',
    'the suite failed, or a file is below the coverage floor',
  )
}
