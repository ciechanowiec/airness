// A tool the member itself declares, resolved the way the member's own code resolves it. A framework's
// command line, such as `next`, has to be the member's copy: it builds and serves the member's code with the
// framework version the member locked.
import { createRequire } from 'node:module'
import path from 'node:path'
import { asRecord, asStringRecord, asText } from './json-shapes.js'
import { type Member, readJson } from './project.js'

/**
 * The entry point of a command a package the member resolves ships.
 * @param member the member.
 * @param packageName the package, as the member declares it.
 * @param binName the command, when it differs from the package name.
 * @returns the absolute path of the command's script, or undefined when the member resolves no such package.
 */
export const memberBin = (member: Member, packageName: string, binName: string = packageName): string | undefined => {
  try {
    const manifest: string = createRequire(path.join(member.root, 'package.json')).resolve(
      `${packageName}/package.json`,
    )
    const bin: unknown = asRecord(readJson(manifest))['bin']
    const script: string = typeof bin === 'string' ? bin : asText(asStringRecord(bin)[binName])
    return script.length > 0 ? path.join(path.dirname(manifest), script) : undefined
  } catch {
    return undefined
  }
}
