// The member's own suite, run by the member's own Vitest under the shipped configuration, with the per-file
// coverage floor. The runner is the member's copy because its specs import it, and a second copy would load
// a second module registry.
import { createRequire } from 'node:module'
import path from 'node:path'
import { runApp } from './app-process.js'
import { clockedEnvironment } from './clock-environment.js'
import { coverageCandidates } from './coverage-policy.js'
import { fromRun } from './exec.js'
import { isBrowserApp } from './facets.js'
import { matchesGlob } from './file-roles.js'
import { failed, type GateResult, passed } from './gate-result.js'
import { isolatedGitEnvironment } from './git-isolation.js'
import { isHarnessPackage, SHIPPED_SPEC_SOURCES } from './harness-packages.js'
import { asRecord, asStringRecord, asText } from './json-shapes.js'
import { harnessDirectory, type Member, readJson } from './project.js'
import { matchesAnyGlob, memberFiles } from './source-selection.js'
import { misplacedSpecs } from './spec-roles.js'
import { withPretest } from './test-invocation.js'
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

// A harness package ships end-to-end specs from its sources, which the e2e gate runs from the built package in
// every browser application rather than in the package itself.
const isShippedSpec = (member: Member, file: string): boolean =>
  isHarnessPackage(asText(asRecord(member.packageJson)['name'])) && matchesGlob(SHIPPED_SPEC_SOURCES, file)

// The member's own suite under the shipped configuration.
const suite = (member: Member): GateResult => {
  if (
    coverageCandidates(presentWorkingTreeFiles(member.root), member.settings, member.siblingPaths, member.hasOwnRuntime)
      .length === 0
  ) {
    return passed('the member holds no source the floor measures, so there is no suite to run')
  }
  const vitest: string | undefined = memberVitest(member)
  if (vitest === undefined) {
    return failed('vitest could not be resolved from the member', [
      'declare vitest and @vitest/coverage-v8 as development dependencies; the member runs its own copy',
    ])
  }
  const config: string = path.join(harnessDirectory(), 'dist', 'vitest.js')
  return withPretest(member, () =>
    fromRun(
      runApp(member, [vitest, 'run', '--config', config, '--coverage'], {
        cwd: member.root,
        // The whole run, its global setup included, reads the managed Git configuration rather than the machine's.
        env: { ...clockedEnvironment(member, '--no-deprecation'), ...isolatedGitEnvironment(member.root) },
      }),
      'the suite passes and every measured file meets the coverage floor',
      'the suite failed, or a file is below the coverage floor',
    ),
  )
}

/** Every spec sits where a runner collects it, the suite passes, and every measured file meets the floor. */
export const tests = (member: Member): GateResult => {
  const files: readonly string[] = memberFiles(presentWorkingTreeFiles(member.root), member.siblingPaths).filter(
    (file: string): boolean => !matchesAnyGlob(file, member.settings.generatedArtefacts),
  )
  const misplaced: readonly string[] = misplacedSpecs(
    files.filter((file: string): boolean => !isShippedSpec(member, file)),
    isBrowserApp(member),
  )
  if (misplaced.length > 0) {
    return failed(`${String(misplaced.length)} spec file(s) no runner collects`, misplaced)
  }
  return suite(member)
}
