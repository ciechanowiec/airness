// The environment files a browser run reads outside verification, and the port of the origin it drives.

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
