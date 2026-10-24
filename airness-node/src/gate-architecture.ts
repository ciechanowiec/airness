// Module architecture: no cycle, no unresolvable import, no production import of a spec or of a
// development-only dependency, under the shipped dependency-cruiser contract; and, where the member has them,
// the Next.js application layer and the declared pure-logic floor, rendered on top of it outside the tree.
import { existsSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { type ArchitectureRule, layerRules } from './architecture-layers.js'
import { fromRun, type GateResult, passed, runNode } from './exec.js'
import { isNextApp } from './facets.js'
import { harnessDirectory, type Member, resolveTool } from './project.js'

const JSON_INDENT: number = 2

// Every source root that exists: a cycle through `tests` or `scripts` is still a cycle, and the analyzer
// refuses a directory it cannot open.
const presentSourceRoots = (member: Member): readonly string[] =>
  member.settings.sourceRoots.filter((root: string): boolean => existsSync(path.join(member.root, root)))

/** The member's modules hold to the shipped architecture contract. */
export const architecture = (member: Member): GateResult => {
  const roots: readonly string[] = presentSourceRoots(member)
  if (roots.length === 0) {
    return passed('the member holds no source directory to cruise')
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
    return fromRun(
      runNode(resolveTool('dependency-cruiser', 'depcruise'), [...roots, '--no-progress', '--config', config], {
        cwd: member.root,
      }),
      'the module architecture holds',
      'the module architecture contract is broken',
    )
  } finally {
    if (directory !== undefined) {
      rmSync(directory, { recursive: true, force: true })
    }
  }
}
