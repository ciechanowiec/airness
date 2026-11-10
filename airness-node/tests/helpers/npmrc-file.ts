// The npm configuration pnpm reads for a project whose own `.npmrc` holds a given text, with no user file, so
// a spec reads an `.npmrc` the way a gate does rather than through a parser of its own.
import path from 'node:path'
import { type NpmConfig, npmConfigOf } from '@/npmrc.js'
import { removeProject, temporaryProject } from './temporary-project.js'

/**
 * The configuration of a project whose `.npmrc` is the given text.
 * @param text the project file's text.
 * @param variables the variables `${NAME}` expands from.
 * @returns the configuration pnpm reads.
 */
export const projectNpmConfig = (text: string, variables: NodeJS.ProcessEnv = {}): NpmConfig => {
  const root: string = temporaryProject({ '.npmrc': text })
  try {
    return npmConfigOf(root, { ...variables, npm_config_userconfig: path.join(root, 'absent.npmrc') })
  } finally {
    removeProject(root)
  }
}
