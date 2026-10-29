// A real node project in a temporary directory of this package's own working tree, which a gate reads exactly
// as it reads a consumer's: through `git ls-files`, which lists its files as untracked ones.
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { layerDefaultsOf } from '@airness/node/layer'
import type { Member } from '@airness/node/project'
import { type LayerDefaults, readSettings } from '@airness/node/settings'
import { onTestFinished } from 'vitest'
import { type PayloadMember, payloadMemberOf } from '@/payload-member.js'

const packageRoot: string = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')

/** The defaults this package contributes to every member that declares it, read from its own manifest. */
const PAYLOAD_LAYER: LayerDefaults = layerDefaultsOf(
  JSON.parse(readFileSync(path.join(packageRoot, 'package.json'), 'utf8')),
)

/**
 * Write a node project into a fresh temporary directory beside the specs.
 * @param files project-relative paths and their contents.
 * @returns the project directory.
 */
const temporaryProject = (files: Readonly<Record<string, string>>): string => {
  const root: string = mkdtempSync(path.join(packageRoot, 'tests', 'ploaness project '))
  for (const [relative, content] of Object.entries(files)) {
    const file: string = path.join(root, relative)
    mkdirSync(path.dirname(file), { recursive: true })
    writeFileSync(file, content)
  }
  return root
}

/**
 * Remove a temporary project.
 * @param root the project directory.
 */
const removeProject = (root: string): void => {
  rmSync(root, { recursive: true, force: true })
}

/**
 * The project as a gate's only member, read as the node layer reads it.
 * @param root the project directory.
 * @param packageJson the manifest the project declares.
 * @param memberPath the member's path in its node project.
 * @returns the member with its Payload facts, the Payload layer's defaults applied.
 */
export const payloadMemberAt = (root: string, packageJson: unknown = {}, memberPath: string = '.'): PayloadMember => {
  const member: Member = {
    root,
    projectRoot: root,
    path: memberPath,
    packageJson,
    settings: readSettings(packageJson, [PAYLOAD_LAYER]),
    hasOwnRuntime: false,
    siblingPaths: [],
  }
  return payloadMemberOf(member)
}

/**
 * Write a node project for the running test, removed when the test finishes.
 * @param files project-relative paths and their contents.
 * @returns the project directory.
 */
export const projectForTest = (files: Readonly<Record<string, string>>): string => {
  const root: string = temporaryProject(files)
  onTestFinished((): void => {
    removeProject(root)
  })
  return root
}

/**
 * Write a node project outside every repository for the running test, removed when the test finishes. A gate
 * that asks git whether a file is tracked reads such a project as one whose every file is.
 * @param files project-relative paths and their contents.
 * @returns the project directory.
 */
export const detachedProjectForTest = (files: Readonly<Record<string, string>>): string => {
  const root: string = mkdtempSync(path.join(os.tmpdir(), 'ploaness project '))
  for (const [relative, content] of Object.entries(files)) {
    const file: string = path.join(root, relative)
    mkdirSync(path.dirname(file), { recursive: true })
    writeFileSync(file, content)
  }
  onTestFinished((): void => {
    removeProject(root)
  })
  return root
}
