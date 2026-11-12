// The Dockerfiles that build a member: every one in the member's own directory, without those of a member nested
// in it, as a commit would carry them. The image gate builds and boots each, and a stack layer reads them for what
// an image needs at build time, so both name the same files.
import { existsSync } from 'node:fs'
import path from 'node:path'
import type { Member } from './project.js'
import { memberFiles } from './source-selection.js'
import { workingTreeFiles } from './working-tree.js'

/**
 * Whether a path names a Dockerfile.
 * @param file a path.
 * @returns true for `Dockerfile`, `Dockerfile.<name>` and `<name>.Dockerfile`.
 */
export const isDockerfile = (file: string): boolean => {
  const name: string = file.slice(file.lastIndexOf('/') + 1)
  return name === 'Dockerfile' || name.endsWith('.Dockerfile') || name.startsWith('Dockerfile.')
}

/**
 * The Dockerfiles that build a member.
 * @param member the member.
 * @returns their paths relative to the member's directory, sorted.
 */
export const memberDockerfiles = (member: Member): readonly string[] =>
  memberFiles(workingTreeFiles(member.root), member.siblingPaths).filter(
    (file: string): boolean => isDockerfile(file) && existsSync(path.join(member.root, file)),
  )
