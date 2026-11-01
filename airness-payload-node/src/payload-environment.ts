// The Payload half of the environment coherence rule. The repository rule (compose files, verifying workflows,
// a Next.js configuration's build-time reads) is the generic layer's; what is Payload's is the validated
// environment module, `src/lib/environment.ts`, the one module a Payload application reads `process.env` in.
// What it reads must be documented, and what it inlines into a built image must reach the image.

/** The module a Payload application reads its environment in. */
export const VALIDATED_ENVIRONMENT_MODULE: string = 'src/lib/environment.ts'

/** The example files that document a repository's environment, the first present one being read. */
export const ENVIRONMENT_EXAMPLE_FILES: readonly string[] = ['.env.example', '.env.sample', '.env.template']

// A bracketed read with a literal name. `process.env` has an index signature, so a variable the project invented
// can only be read with brackets, while a variable a runtime declares, `NODE_ENV` above all, is read with a dot.
// The bracketed form therefore separates project configuration, which the example file owes a reader, from the
// ambient variables no example file should claim to document.
const BRACKETED_READ: RegExp = /process\.env\[\s*(?<quote>['"])(?<name>[A-Za-z_]\w*)\k<quote>\s*\]/g

// A dotenv assignment: a name at the start of a line, optionally exported, followed by `=`.
const EXAMPLE_ASSIGNMENT: RegExp = /^[ \t]*(?:export[ \t]+)?(?<name>[A-Za-z_]\w*)[ \t]*=/gm

// Both access forms of a name the bundler inlines. `NEXT_PUBLIC_` is reserved by the framework for values the
// project invents, so a dotted read of one cannot be naming an ambient variable.
const INLINED_DOTTED_READ: RegExp = /process\.env\.(?<name>NEXT_PUBLIC_\w+)/g
const INLINED_BRACKETED_READ: RegExp = /process\.env\[\s*(?<quote>['"])(?<name>NEXT_PUBLIC_\w+)\k<quote>\s*\]/g

// A build-argument declaration, with or without a default: `ARG NAME=x` declares NAME just as `ARG NAME` does.
const ARG_DECLARATION: RegExp = /^[ \t]*ARG[ \t]+(?<names>\S.*)$/i
const ARGUMENT_SEPARATOR: RegExp = /[ \t]+/
const CONTINUATION: RegExp = /\\\r?\n/g

const namesMatching = (source: string, pattern: RegExp): readonly string[] =>
  [...source.matchAll(pattern)].map((match: RegExpExecArray): string => String(match.groups?.['name']))

const uniqueSorted = (names: readonly string[]): readonly string[] =>
  [...new Set<string>(names)].toSorted((left: string, right: string): number => left.localeCompare(right))

/** A Dockerfile that builds the member, and its text. */
export interface DockerfileSource {
  readonly file: string
  readonly content: string
}

/** What the rule reads, already read. */
export interface EnvironmentInputs {
  /** The validated environment module, or undefined when the member has none. */
  readonly module: string | undefined
  /** The repository's example file, or undefined when it has none. */
  readonly example: string | undefined
  /** The Dockerfiles that build the member. */
  readonly dockerfiles: readonly DockerfileSource[]
  /** The variables the member reads its own origin from, which verification sets for each run. */
  readonly serverUrlVariables: readonly string[]
}

/**
 * Whether a path names a Dockerfile.
 * @param file a path.
 * @returns true for `Dockerfile`, `Dockerfile.<name>` and `<name>.Dockerfile`.
 */
export const isDockerfile = (file: string): boolean => {
  const name: string = file.slice(file.lastIndexOf('/') + 1)
  return name === 'Dockerfile' || name.endsWith('.Dockerfile') || name.startsWith('Dockerfile.')
}

const declaredBuildArguments = (dockerfile: string): readonly string[] =>
  dockerfile
    .replaceAll(CONTINUATION, ' ')
    .split('\n')
    .flatMap((line: string): readonly string[] => {
      const names: string | undefined = ARG_DECLARATION.exec(line)?.groups?.['names']
      return names === undefined
        ? []
        : names
            .split(ARGUMENT_SEPARATOR)
            .map((token: string): string => token.split('=', 1)[0] ?? '')
            .filter((name: string): boolean => name.length > 0)
    })

const undocumented = (module: string, example: string | undefined): readonly string[] => {
  const documented: ReadonlySet<string> = new Set(
    example === undefined ? [] : namesMatching(example, EXAMPLE_ASSIGNMENT),
  )
  return uniqueSorted(namesMatching(module, BRACKETED_READ))
    .filter((name: string): boolean => !documented.has(name))
    .map(
      (name: string): string =>
        `${name}: read by ${VALIDATED_ENVIRONMENT_MODULE} but documented in no example file ` +
        `(${ENVIRONMENT_EXAMPLE_FILES.join(', ')} at the repository root); add "${name}=" with a comment saying what it holds`,
    )
}

const undeclaredArguments = (module: string, dockerfiles: readonly DockerfileSource[]): readonly string[] => {
  if (dockerfiles.length === 0) {
    return []
  }
  const declared: ReadonlySet<string> = new Set(
    dockerfiles.flatMap((dockerfile: DockerfileSource): readonly string[] =>
      declaredBuildArguments(dockerfile.content),
    ),
  )
  const files: string = dockerfiles.map((dockerfile: DockerfileSource): string => dockerfile.file).join(', ')
  return uniqueSorted([...namesMatching(module, INLINED_DOTTED_READ), ...namesMatching(module, INLINED_BRACKETED_READ)])
    .filter((name: string): boolean => !declared.has(name))
    .map(
      (name: string): string =>
        `${name}: inlined into the image at build time by ${VALIDATED_ENVIRONMENT_MODULE} but declared by no ARG in ` +
        `${files}, so docker discards the --build-arg that supplies it; add "ARG ${name}" to the stage that runs the build`,
    )
}

// Verification serves the application on a port chosen for each run and names the origin in these variables
// only once the build is made, so a read the bundler replaces at build time would keep the build's value.
const inlinedOrigins = (module: string, variables: readonly string[]): readonly string[] =>
  uniqueSorted([...namesMatching(module, INLINED_DOTTED_READ), ...namesMatching(module, INLINED_BRACKETED_READ)])
    .filter((name: string): boolean => variables.includes(name))
    .map(
      (name: string): string =>
        `${name}: named in airness.serverUrlVariables but inlined at build time by ${VALIDATED_ENVIRONMENT_MODULE}, ` +
        'so the application keeps the origin it was built with rather than the one a verification run serves on; ' +
        'read it from a parameter holding process.env, which the bundler cannot replace',
    )

/**
 * Every variable the validated environment module reads that does not reach where it has to.
 * @param inputs the files, already read.
 * @returns one finding per variable, sorted by name within each rule.
 */
export const findEnvironmentViolations = (inputs: EnvironmentInputs): readonly string[] =>
  inputs.module === undefined
    ? []
    : [
        ...undocumented(inputs.module, inputs.example),
        ...undeclaredArguments(inputs.module, inputs.dockerfiles),
        ...inlinedOrigins(inputs.module, inputs.serverUrlVariables),
      ]
