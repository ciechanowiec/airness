// A real node project in a temporary directory of this package's own working tree, which a gate reads
// exactly as it reads a consumer's: through `git ls-files`, which lists its files as untracked ones.
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Member } from '@/project.js'
import { readSettings } from '@/settings.js'

/** The directory of this package, where the shipped configurations live. */
const harnessRoot: string = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')

/**
 * Write a node project into a fresh temporary directory beside the specs.
 * @param files project-relative paths and their contents.
 * @returns the project directory.
 */
export const temporaryProject = (files: Readonly<Record<string, string>>): string => {
  const root: string = mkdtempSync(path.join(harnessRoot, 'tests', 'airness project '))
  for (const [relative, content] of Object.entries(files)) {
    const file: string = path.join(root, relative)
    mkdirSync(path.dirname(file), { recursive: true })
    writeFileSync(file, content)
  }
  return root
}

/**
 * The project as a gate's only member, read from its package.json.
 * @param root the project directory.
 * @param packageJson the manifest the project declares.
 * @returns the member.
 */
export const memberAt = (root: string, packageJson: unknown = {}): Member => ({
  root,
  projectRoot: root,
  path: '.',
  packageJson,
  settings: readSettings(packageJson),
  hasOwnRuntime: false,
  siblingPaths: [],
})

/**
 * Remove a temporary project.
 * @param root the project directory.
 */
export const removeProject = (root: string): void => {
  rmSync(root, { recursive: true, force: true })
}
