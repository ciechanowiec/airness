// The TypeScript sources of a Payload member that the Payload source rules read: every file under its source
// roots that a commit would carry, without the files a generator owns or another member's files.
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import type { SpecSource } from '@airness/node/axe-coverage'
import { TYPESCRIPT_SOURCE_EXTENSIONS } from '@airness/node/file-roles'
import { matchesAnyGlob, memberFiles } from '@airness/node/source-selection'
import { workingTreeFiles } from '@airness/node/working-tree'
import type { PayloadMember } from './payload-member.js'

const isTypeScript = (file: string): boolean =>
  TYPESCRIPT_SOURCE_EXTENSIONS.some((extension: string): boolean => file.endsWith(extension))

/**
 * The member's TypeScript sources the Payload rules read, each with its text.
 * @param target the member.
 * @returns the sources, by path relative to the member.
 */
export const payloadSourcesOf = (target: PayloadMember): readonly SpecSource[] => {
  const { root, settings, siblingPaths } = target.member
  return memberFiles(workingTreeFiles(root), siblingPaths)
    .filter(
      (file: string): boolean =>
        isTypeScript(file) &&
        settings.sourceRoots.some((sourceRoot: string): boolean => file.startsWith(`${sourceRoot}/`)) &&
        !matchesAnyGlob(file, settings.generatedArtefacts) &&
        existsSync(path.join(root, file)),
    )
    .map((file: string): SpecSource => ({ path: file, source: readFileSync(path.join(root, file), 'utf8') }))
}
