// The node project's pnpm-workspace.yaml, read as the settings pnpm takes from it.
import path from 'node:path'
import { parse } from 'yaml'
import { readText } from './project.js'

/** The file pnpm reads its project settings from, at the node project root. */
const WORKSPACE_FILE: string = 'pnpm-workspace.yaml'

/**
 * Read a node project's workspace file.
 * @param projectRoot the node project directory.
 * @returns the parsed document, or undefined when the file is absent or is not YAML.
 */
export const readWorkspaceFile = (projectRoot: string): unknown => {
  const text: string | undefined = readText(path.join(projectRoot, WORKSPACE_FILE))
  try {
    return text === undefined ? undefined : parse(text)
  } catch {
    return undefined
  }
}
