// The install configuration of a node project, read from its root.
import { readdirSync } from 'node:fs'
import path from 'node:path'
import type { InstallConfigInputs } from './install-config.js'
import { npmConfigOf } from './npmrc.js'
import { readJson } from './project.js'
import { readWorkspaceFile } from './workspace-file.js'

/**
 * Read what the install-config rules judge at a node project's root.
 * @param projectRoot the node project directory.
 * @returns the workspace file, the manifest, the npm configuration and the root's file names.
 */
export const installConfigOf = (projectRoot: string): InstallConfigInputs => ({
  workspace: readWorkspaceFile(projectRoot),
  manifest: readJson(path.join(projectRoot, 'package.json')),
  npmrc: npmConfigOf(projectRoot),
  files: readdirSync(projectRoot),
})
