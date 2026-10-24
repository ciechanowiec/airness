// The npm dependency evidence of the node project, written beside the gate's result for the dependency
// policies to read. It passes whenever the evidence could be written, because it judges nothing.
import { writeFileSync } from 'node:fs'
import path from 'node:path'
import { failed, type GateContext, type GateResult, passed } from './exec.js'
import { readResolvedSet } from './gate-install-scripts.js'
import { installConfigOf } from './install-config-files.js'
import { INVENTORY_FILE, type Inventory, inventoryOf } from './inventory.js'
import type { Member } from './project.js'
import { missingFindings, type ResolvedSet } from './resolved-set.js'
import { ROOT_MEMBER_PATH } from './workspace.js'

const JSON_INDENT: number = 2

/** Write the inventory of every resolved package, once for the whole node project. */
export const npmInventory = (member: Member, context: GateContext): GateResult => {
  if (member.path !== ROOT_MEMBER_PATH) {
    return passed('the project root inventories the packages of every member')
  }
  const read: ResolvedSet | readonly string[] = readResolvedSet(member.projectRoot, installConfigOf(member.projectRoot))
  if (!('installed' in read)) {
    return failed('the npm dependency evidence cannot be produced', read)
  }
  if (read.missing.length > 0) {
    return failed('the npm dependency evidence cannot be produced', missingFindings(read.missing))
  }
  const inventory: Inventory = inventoryOf(read)
  if (context.evidenceDirectory === undefined) {
    return passed(`${String(inventory.packages.length)} resolved package(s), written nowhere without --result`)
  }
  writeFileSync(
    path.join(context.evidenceDirectory, INVENTORY_FILE),
    `${JSON.stringify(inventory, null, JSON_INDENT)}\n`,
  )
  return {
    ...passed(`${String(inventory.packages.length)} resolved package(s) written to ${INVENTORY_FILE}`),
    evidence: [INVENTORY_FILE],
  }
}
