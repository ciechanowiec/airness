// Module architecture: no cycle, no unresolvable import, no production import of a spec, of test code or of a
// development-only dependency, under the shipped dependency-cruiser contract; and, where the member has them,
// the Next.js application layer and the declared pure-logic floor, rendered on top of it outside the tree, with the
// application's routes directory under `src` where that layer is drawn.
import { existsSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { type ArchitectureRule, layerRules, misplacedRouteDirectoryFindings } from './architecture-layers.js'
import { importGraphOf } from './change-scope-policy.js'
import { failed, fromRun, type GateResult, passed, type RunResult, runNode, withOutput } from './exec.js'
import { isNextApp } from './facets.js'
import { CLOUDFRONT_RUNTIME, rolesFor } from './javascript-runtimes.js'
import { parseJsonc } from './json-shapes.js'
import { harnessDirectory, type Member, resolveTool } from './project.js'
import { allowedJavaScriptFiles, memberFiles } from './source-selection.js'
import { presentWorkingTreeFiles } from './working-tree.js'

const JSON_INDENT: number = 2

// Every source root that exists: a cycle through `tests` or `scripts` is still a cycle, and the analyzer
// refuses a directory it cannot open.
const presentSourceRoots = (member: Member): readonly string[] =>
  member.settings.sourceRoots.filter((root: string): boolean => existsSync(path.join(member.root, root)))

/** The member's modules hold to the shipped architecture contract. */
const cruise = (member: Member, options: readonly string[]): RunResult => {
  const roots: readonly string[] = presentSourceRoots(member)
  if (roots.length === 0) {
    return { code: 0, output: '', stdout: '{"modules":[]}' }
  }
  const shipped: string = path.join(harnessDirectory(), 'configs', 'dependency-cruiser.json')
  const rules: readonly ArchitectureRule[] = layerRules(
    isNextApp(member),
    member.settings.pureLogicRoots,
    member.settings.sourceRoots,
    allowedJavaScriptFiles(
      presentWorkingTreeFiles(member.root),
      rolesFor(member.settings.javascriptRuntimes, CLOUDFRONT_RUNTIME),
      member.siblingPaths,
    ),
  )
  // Every production root carries the test-code rule, so the contract is always rendered, even when it adds
  // nothing to the shipped configuration it extends.
  const directory: string = mkdtempSync(path.join(tmpdir(), 'airness-arch-'))
  try {
    const config: string = path.join(directory, 'dependency-cruiser.json')
    writeFileSync(config, `${JSON.stringify({ extends: shipped, forbidden: rules }, null, JSON_INDENT)}\n`)
    return runNode(
      resolveTool('dependency-cruiser', 'depcruise'),
      [...roots, '--no-progress', '--config', config, ...options],
      { cwd: member.root },
    )
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
}

const cruised = (member: Member): GateResult =>
  presentSourceRoots(member).length === 0
    ? passed('the member holds no source directory to cruise')
    : fromRun(cruise(member, []), 'the module architecture holds', 'the module architecture contract is broken')

/** The member's modules hold to the shipped architecture contract, and a Next.js member's routes sit under `src`. */
export const architecture = (member: Member): GateResult => {
  const misplaced: readonly string[] = misplacedRouteDirectoryFindings(
    isNextApp(member),
    memberFiles(presentWorkingTreeFiles(member.root), member.siblingPaths),
  )
  const result: GateResult = cruised(member)
  return misplaced.length === 0
    ? result
    : withOutput(
        failed('the Next.js routes sit outside src', [...misplaced, ...(result.ok ? [] : result.findings)]),
        result.output ?? '',
      )
}

/** Read the architecture analyzer's graph; malformed output or a failed analyzer is never an empty graph. */
export const importGraph = (member: Member): Readonly<Record<string, readonly string[]>> | GateResult => {
  const result: RunResult = cruise(member, ['--output-type', 'json'])
  const graph: Readonly<Record<string, readonly string[]>> | undefined = importGraphOf(parseJsonc(result.stdout).value)
  return graph !== undefined && result.code === 0
    ? graph
    : failed('the import graph could not be read', [result.output])
}
