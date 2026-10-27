// Module architecture: no cycle, no unresolvable import, no production import of a spec or of a
// development-only dependency, under the shipped dependency-cruiser contract; and, where the member has them,
// the Next.js application layer and the declared pure-logic floor, rendered on top of it outside the tree.
import { existsSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { type ArchitectureRule, layerRules } from './architecture-layers.js'
import { importGraphOf } from './change-scope-policy.js'
import { failed, fromRun, type GateResult, passed, type RunResult, runNode } from './exec.js'
import { isNextApp } from './facets.js'
import { parseJsonc } from './json-shapes.js'
import { harnessDirectory, type Member, resolveTool } from './project.js'

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
  )
  const directory: string | undefined =
    rules.length === 0 ? undefined : mkdtempSync(path.join(tmpdir(), 'airness-arch-'))
  try {
    const config: string = directory === undefined ? shipped : path.join(directory, 'dependency-cruiser.json')
    if (directory !== undefined) {
      writeFileSync(config, `${JSON.stringify({ extends: shipped, forbidden: rules }, null, JSON_INDENT)}\n`)
    }
    return runNode(
      resolveTool('dependency-cruiser', 'depcruise'),
      [...roots, '--no-progress', '--config', config, ...options],
      { cwd: member.root },
    )
  } finally {
    if (directory !== undefined) {
      rmSync(directory, { recursive: true, force: true })
    }
  }
}

/** The member's modules hold to the shipped architecture contract. */
export const architecture = (member: Member): GateResult =>
  presentSourceRoots(member).length === 0
    ? passed('the member holds no source directory to cruise')
    : fromRun(cruise(member, []), 'the module architecture holds', 'the module architecture contract is broken')

/** Read the architecture analyzer's graph; malformed output or a failed analyzer is never an empty graph. */
export const importGraph = (member: Member): Readonly<Record<string, readonly string[]>> | GateResult => {
  const result: RunResult = cruise(member, ['--output-type', 'json'])
  const graph: Readonly<Record<string, readonly string[]>> | undefined = importGraphOf(parseJsonc(result.stdout).value)
  return graph !== undefined && result.code === 0
    ? graph
    : failed('the import graph could not be read', [result.output])
}
