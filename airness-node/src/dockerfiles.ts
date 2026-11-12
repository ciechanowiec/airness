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

// A command that installs packages from a registry, as each package manager spells it: `npm ci`, `npm install` and
// `npm i`, `pnpm install`, `pnpm i`, `pnpm add`, `pnpm fetch` and `pnpm deploy`, `yarn` alone, `yarn install` and
// `yarn add`, and `bun install`, `bun i` and `bun add`.
const PACKAGE_INSTALLS: readonly RegExp[] = [
  /\bnpm\s+(?:ci|install|i)\b/u,
  /\bpnpm\s+(?:install|i|add|fetch|deploy)\b/u,
  /\byarn\s+(?:install|add)\b/u,
  /\byarn\s*(?:$|&&|;|\|)/u,
  /\bbun\s+(?:install|i|add)\b/u,
]

// A `RUN` instruction, with the lines a trailing backslash continues joined to it and every comment line left out.
const runInstructions = (dockerfile: string): readonly string[] =>
  dockerfile
    .replaceAll(/\\\r?\n/gu, ' ')
    .split(/\r?\n/u)
    .map((line: string): string => line.trim())
    .filter((line: string): boolean => /^RUN\s/iu.test(line))

/**
 * Whether a Dockerfile installs packages from a registry while it builds.
 * @param dockerfile the Dockerfile's text.
 * @returns true when one of its `RUN` instructions runs a package manager's install.
 */
export const doesInstallPackages = (dockerfile: string): boolean =>
  runInstructions(dockerfile).some((instruction: string): boolean =>
    PACKAGE_INSTALLS.some((install: RegExp): boolean => install.test(instruction)),
  )
