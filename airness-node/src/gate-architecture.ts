// Module architecture: no cycle, no unresolvable import, no production import of a spec or of a
// development-only dependency, under the shipped dependency-cruiser contract.
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fromRun, type GateResult, passed, runNode } from './exec.js'
import { harnessDirectory, type Member, resolveTool } from './project.js'

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
  const config: string = path.join(harnessDirectory(), 'configs', 'dependency-cruiser.json')
  return fromRun(
    runNode(resolveTool('dependency-cruiser', 'depcruise'), [...roots, '--no-progress', '--config', config], {
      cwd: member.root,
    }),
    'the module architecture holds',
    'the module architecture contract is broken',
  )
}
