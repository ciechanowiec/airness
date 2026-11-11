// The environment files a browser run reads outside verification, and the port of the origin it drives.
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { parseEnv } from 'node:util'

/**
 * The environment files a run reads, highest precedence first. `.env.local` leads because it is the
 * personal, untracked override of `.env`; the managed `.gitignore` ignores both, which is what makes
 * these the two a project can be assumed to keep out of the tree.
 */
export const RUN_ENVIRONMENT_FILES: readonly string[] = ['.env.local', '.env']

/**
 * The environment files a run should read, in the order it must read them.
 * @param isExistingFile whether a repository-relative path names a file that exists.
 * @returns the present subset of {@link RUN_ENVIRONMENT_FILES}, highest precedence first.
 */
export const runEnvironmentFiles = (isExistingFile: (relativePath: string) => boolean): readonly string[] =>
  RUN_ENVIRONMENT_FILES.filter((file: string): boolean => isExistingFile(file))

/** Read local application configuration for tools that import it, preserving the caller's environment. */
export const localEnvironment = (
  directory: string,
  inherited: Readonly<Record<string, string | undefined>> = process.env,
): Readonly<Record<string, string>> => {
  const files: readonly string[] = runEnvironmentFiles((file: string): boolean =>
    existsSync(path.join(directory, file)),
  )
  return Object.fromEntries(
    files
      .toReversed()
      .flatMap((file: string): readonly [string, string | undefined][] => {
        const content: string = readFileSync(path.join(directory, file), 'utf8')
        return Object.entries(parseEnv(content))
      })
      .filter(
        (entry: [string, string | undefined]): entry is [string, string] =>
          entry[1] !== undefined && inherited[entry[0]] === undefined,
      ),
  )
}

/**
 * The environment of a tool that imports the application's configuration without running the application: the
 * caller's own values win over the environment files, which win over the inert placeholders a stack layer names.
 * Only the names the caller does not already carry are returned, since the caller's environment is inherited.
 * @param directory the member's directory, which holds its environment files.
 * @param placeholders the analysis placeholders the declared layers contribute.
 * @param inherited the caller's environment.
 * @returns the variables to add to the tool's environment.
 */
export const analysisEnvironment = (
  directory: string,
  placeholders: Readonly<Record<string, string>>,
  inherited: Readonly<Record<string, string | undefined>> = process.env,
): Readonly<Record<string, string>> => ({
  ...Object.fromEntries(
    Object.entries(placeholders).filter(([name]: readonly [string, string]): boolean => inherited[name] === undefined),
  ),
  ...localEnvironment(directory, inherited),
})

/**
 * The port an origin names, as a string a child process's environment can carry.
 *
 * A project declares the origin its application serves, and the harness starts that application itself.
 * Reading the origin without reading the port meant the two disagreed: the server was started on the
 * framework's default while the runner waited on the declared one, so the only setting that exists to
 * describe a non-default port made the run hang rather than work.
 * @param serverUrl the declared origin.
 * @returns the port, or undefined when the origin names none and the default applies.
 */
export const portOf = (serverUrl: string): string | undefined => {
  try {
    const port: string = new URL(serverUrl).port
    return port.length > 0 ? port : undefined
  } catch {
    // A malformed origin is the project's to fix, and the gate that drives it will say so far more
    // clearly than a crash inside a configuration file that no stack trace points at.
    return undefined
  }
}
