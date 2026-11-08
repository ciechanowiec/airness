// The CycloneDX bill of materials of the node project, written beside the gate's result for the repository's
// SBOM merge and advisory scan to read. Like the inventory it judges nothing: it passes whenever the document
// could be written from a resolved set the lockfile and the installation agree on.
import path from 'node:path'
import { readResolvedSet } from './gate-install-scripts.js'
import { failed, type GateContext, type GateResult, passed } from './gate-result.js'
import { installConfigOf } from './install-config-files.js'
import { asRecord, asText } from './json-shapes.js'
import { harnessDirectory, type Member, readJson, writeJson } from './project.js'
import { missingFindings, type ResolvedSet } from './resolved-set.js'
import { SBOM_FILE, sbomOf } from './sbom.js'
import { ROOT_MEMBER_PATH } from './workspace.js'

const harnessManifest = (): string => path.join(harnessDirectory(), 'package.json')

const versionOf = (manifest: unknown): string => asText(asRecord(manifest)['version'])

const harnessVersion = (): string => versionOf(readJson(harnessManifest()))

/** Write the CycloneDX document of every resolved package, once for the whole node project. */
export const npmSbom = (member: Member, context: GateContext): GateResult => {
  if (member.path !== ROOT_MEMBER_PATH) {
    return passed('the project root describes the packages of every member')
  }
  const read: ResolvedSet | readonly string[] = readResolvedSet(member.projectRoot, installConfigOf(member.projectRoot))
  if (!('installed' in read)) {
    return failed('the npm SBOM cannot be produced', read)
  }
  if (read.missing.length > 0) {
    return failed('the npm SBOM cannot be produced', missingFindings(read.missing))
  }
  const name: string = asText(asRecord(member.packageJson)['name'])
  const document: Readonly<Record<string, unknown>> = sbomOf(read, {
    name: name.length > 0 ? name : path.basename(member.root),
    version: versionOf(member.packageJson),
    harnessVersion: harnessVersion(),
  })
  const count: number = read.installed.length + read.elsewhere.length
  if (context.evidenceDirectory === undefined) {
    return passed(`${String(count)} resolved package(s), written nowhere without --result`)
  }
  writeJson(path.join(context.evidenceDirectory, SBOM_FILE), document)
  return { ...passed(`${String(count)} resolved package(s) described in ${SBOM_FILE}`), evidence: [SBOM_FILE] }
}
