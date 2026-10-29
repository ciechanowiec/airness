// The gate that runs before every other Payload gate. The Payload layer governs Payload CMS applications, so
// it refuses a node project that holds none, and it holds every Payload application to the versions of
// Payload, its first-party packages and its renderer the layer decides. A workspace may hold packages that
// are no Payload application, such as a shared library, so the refusal is asked of the whole node project.
import path from 'node:path'
import { declaredDependencies, readJsonFile, readKey } from './json-shapes.js'
import { failed, type GateResult, passed } from './outcome.js'
import type { PayloadMember } from './payload-member.js'
import { findPinViolations, type InstalledPackage, pinnedVersionOf, REQUIRED_PINS } from './payload-pins.js'

// What the member installed, read from its own node_modules, where pnpm links every direct dependency.
const installedVersion = (root: string, name: string): string | undefined => {
  const version: unknown = readKey(readJsonFile(path.join(root, 'node_modules', name, 'package.json')), 'version')
  return typeof version === 'string' ? version : undefined
}

const pinFindings = (target: PayloadMember): readonly string[] => {
  const declared: readonly string[] = Object.keys(declaredDependencies(target.member.packageJson))
  const judged: readonly string[] = [
    ...new Set<string>([
      ...Object.keys(REQUIRED_PINS),
      ...declared.filter((name: string): boolean => pinnedVersionOf(name) !== undefined),
    ]),
  ]
  const installed: readonly InstalledPackage[] = judged.map(
    (name: string): InstalledPackage => ({ name, version: installedVersion(target.member.root, name) }),
  )
  return findPinViolations(declared, installed).map((finding: string): string => `${target.member.path}: ${finding}`)
}

/**
 * Hold the node project to being a Payload application on the versions the layer decides.
 * @param target the member being judged.
 * @param members every member of the node project.
 * @returns the gate result: the project member answers for the whole project, every Payload member for its
 * own pins.
 */
export const preflight = (target: PayloadMember, members: readonly PayloadMember[]): GateResult => {
  const isProject: boolean = target.member.path === '.'
  const noPayload: readonly string[] =
    isProject && !members.some((member: PayloadMember): boolean => member.isPayload)
      ? [
          'no package in this node project declares "payload"; the Payload layer governs Payload CMS applications, ' +
            'so inherit airness-parent-node instead of airness-parent-payload for any other kind',
        ]
      : []
  const findings: readonly string[] = [...noPayload, ...(target.isPayload ? pinFindings(target) : [])]
  if (findings.length > 0) {
    return failed('the node project is no Payload application on the versions the layer decides', findings)
  }
  return passed(target.isPayload ? 'a Payload application on the pinned versions' : 'no Payload in this member')
}
