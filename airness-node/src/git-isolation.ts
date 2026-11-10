// The Git configuration a test run reads. Every `git` a test starts would otherwise read the global and system
// configuration of the machine running it, so a developer's hooks, commit signing or ignore file, or a CI image's
// defaults, could decide a verdict. `GIT_CONFIG_GLOBAL` replaces the global file with the managed one, which turns
// hooks, signing and the machine's ignore and attribute files off, and `GIT_CONFIG_NOSYSTEM` drops the system one.
import path from 'node:path'
import { type RunResult, run } from './exec.js'

/** The repository path of the managed Git configuration every test run reads as its global one. */
const TEST_GIT_CONFIG: string = '.airness/test.gitconfig'

// The repository Maven names for the build, else the one Git places the directory in. A directory outside any
// repository has no managed file, and naming its absent one still keeps the machine's global and system files out.
const repositoryRootOf = (directory: string): string => {
  const named: string | undefined = process.env['AIRNESS_REPOSITORY_ROOT']
  if (named !== undefined) {
    return named
  }
  const shown: RunResult = run('git', ['rev-parse', '--show-toplevel'], {
    cwd: directory,
  })
  return shown.code === 0 ? shown.stdout.trim() : directory
}

/**
 * The variables under which every Git a test starts reads the managed configuration alone.
 * @param directory a directory of the repository whose tests run.
 * @returns `GIT_CONFIG_GLOBAL` naming the repository's managed file, and `GIT_CONFIG_NOSYSTEM`.
 */
export const isolatedGitEnvironment = (directory: string): Readonly<Record<string, string>> => ({
  GIT_CONFIG_NOSYSTEM: '1',
  GIT_CONFIG_GLOBAL: path.join(repositoryRootOf(directory), TEST_GIT_CONFIG),
})
