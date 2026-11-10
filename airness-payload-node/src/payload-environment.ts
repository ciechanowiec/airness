// The Payload half of the environment coherence rule. The repository rule (compose files, verifying workflows,
// a Next.js configuration's build-time reads) is the generic layer's; what is Payload's is the validated
// environment module, `src/lib/environment.ts`, the one module a Payload application reads `process.env` in.
// What it reads must be documented, and what it inlines into a built image must reach the image.

import { stripComments } from '@airness/node/source-text'

/** The module a Payload application reads its environment in. */
export const VALIDATED_ENVIRONMENT_MODULE: string = 'src/lib/environment.ts'

/** The example files that document a repository's environment, the first present one being read. */
export const ENVIRONMENT_EXAMPLE_FILES: readonly string[] = ['.env.example', '.env.sample', '.env.template']

// A bracketed read with a literal name, on `process.env` or on any other receiver, since the module may read a
// parameter that holds the environment, `source['NAME']`, which the bundler cannot replace. The bracket follows
// its receiver directly, so an array literal after a keyword, `return ['NAME']`, is not read as one. `process.env`
// has an index signature, so a variable the project invented can only be read with brackets, while a variable a
// runtime declares, `NODE_ENV` above all, is read with a dot. The bracketed form therefore separates project
// configuration, which the example file owes a reader, from the ambient variables no example file should claim to
// document.
const BRACKETED_READ: RegExp = /(?:[\w$)\]]|\?\.)\[\s*(?<quote>['"`])(?<name>[a-z_]\w*)\k<quote>\s*\]/gi

// A type annotation up to the `=` of its default, generic arguments with their commas included, two levels deep.
const ANNOTATION: string = '(?::(?:[^=;(),<>]|<[^<>]*(?:<[^<>]*>[^<>]*)*>)*)?'

// A name that holds the environment: one typed as it, `(source: NodeJS.ProcessEnv)`, or one given `process.env`
// itself as its value or default, `const env = process.env` or `(source = process.env)`.
const WHOLE_ENVIRONMENT: string = String.raw`process\.env\b(?!\s*(?:\??\.|\[))`
const ENVIRONMENT_HOLDER: RegExp = new RegExp(
  String.raw`(?<name>[A-Za-z_$][\w$]*)\s*(?::\s*(?:NodeJS\.)?ProcessEnv\b|${ANNOTATION}=\s*${WHOLE_ENVIRONMENT})`,
  'g',
)

// A destructuring pattern with its annotation and the value it takes apart: `const { NAME } = process.env`,
// `({ NAME }: NodeJS.ProcessEnv) =>` or `const { NAME } = source`.
const DESTRUCTURING: RegExp = new RegExp(
  String.raw`\{(?<keys>[^{}]*)\}\s*(?::\s*(?<type>[\w$.]+)\s*)?` +
    String.raw`(?:=\s*(?<value>process\.env|[A-Za-z_$][\w$]*)\b(?!\s*(?:\??\.|\[|\()))?`,
  'g',
)

const PROCESS_ENVIRONMENT: string = 'process.env'
const PROCESS_ENVIRONMENT_TYPE: RegExp = /^(?:NodeJS\.)?ProcessEnv$/

// The name one entry of a pattern takes out: `NAME`, `NAME: alias`, `NAME = fallback` or `'NAME': alias`. A rest
// element names no variable.
const PATTERN_KEY: RegExp = /^\s*(?<quote>['"]?)(?<name>[a-z_]\w*)\k<quote>\s*(?:[:=]|$)/i

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

const holdersIn = (code: string): ReadonlySet<string> =>
  new Set([PROCESS_ENVIRONMENT, ...namesMatching(code, ENVIRONMENT_HOLDER)])

const isEnvironmentTakenApart = (match: RegExpExecArray, holders: ReadonlySet<string>): boolean => {
  const value: string | undefined = match.groups?.['value']
  const type: string | undefined = match.groups?.['type']
  return (value !== undefined && holders.has(value)) || (type !== undefined && PROCESS_ENVIRONMENT_TYPE.test(type))
}

const destructuredNames = (code: string): readonly string[] => {
  const holders: ReadonlySet<string> = holdersIn(code)
  return [...code.matchAll(DESTRUCTURING)]
    .filter((match: RegExpExecArray): boolean => isEnvironmentTakenApart(match, holders))
    .flatMap((match: RegExpExecArray): readonly string[] => String(match.groups?.['keys']).split(','))
    .flatMap((entry: string): readonly string[] => {
      const name: string | undefined = PATTERN_KEY.exec(entry)?.groups?.['name']
      return name === undefined ? [] : [name]
    })
}

// Every variable the module reads by its name: a bracketed read, or a name a pattern takes out of the environment.
// A schema library handed the whole object names none, which the layer guide states.
const documentedNamesOf = (module: string): readonly string[] => {
  const code: string = stripComments(module)
  return [...namesMatching(code, BRACKETED_READ), ...destructuredNames(code)]
}

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
  return uniqueSorted(documentedNamesOf(module))
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
