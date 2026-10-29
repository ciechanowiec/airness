// The Payload layer's refused packages, read from the node project's lockfile, so a package any member reaches
// at any depth is judged. The lockfile belongs to the node project, so the project member answers for all.
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { failed, type GateResult, passed } from './outcome.js'
import { findBlockedPackages, resolvedPackagesIn } from './payload-blocklist.js'
import type { PayloadMember } from './payload-member.js'

const LOCKFILE: string = 'pnpm-lock.yaml'

/**
 * Fail on every package the Payload layer refuses that the node project's lockfile resolves.
 * @param target the member being judged.
 * @returns the gate result.
 */
export const payloadBlocklist = (target: PayloadMember): GateResult => {
  if (target.member.path !== '.') {
    return passed("judged at the node project's lockfile")
  }
  const lockfile: string = path.join(target.member.projectRoot, LOCKFILE)
  if (!existsSync(lockfile)) {
    return failed('the node project has no lockfile to read', [
      `${LOCKFILE} is missing; commit the lockfile pnpm install writes`,
    ])
  }
  const resolved: readonly string[] = resolvedPackagesIn(readFileSync(lockfile, 'utf8'))
  const findings: readonly string[] = findBlockedPackages(resolved)
  return findings.length > 0
    ? failed(`${String(findings.length)} package(s) the Payload layer refuses are resolved`, findings)
    : passed(`none of the ${String(resolved.length)} resolved package(s) is refused by the Payload layer`)
}
