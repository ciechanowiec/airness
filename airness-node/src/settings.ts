// The facts a node project may declare under the `airness` key of its package.json. Every field is
// optional and every default is strict, so a project that declares nothing still receives the full
// contract.
//
// The set is deliberately small: where first-party source lives, which files a generator owns, which
// files are framework glue, and which hand-written JavaScript a tool forces on the project. There is no
// field that turns a gate off. An exclusion is an exclusion by file role, so an entry that names no role
// is not honoured, which leaves the gate stricter rather than looser.
import { hasExtension, JAVASCRIPT_EXTENSIONS, matchesGlob, matchesRole, type RolePattern } from './file-roles.js'
import { isJavascriptRuntime, JAVASCRIPT_RUNTIMES, type RuntimeRole } from './javascript-runtimes.js'
import { asRecord, asText, isArray, isRecord } from './json-shapes.js'

/** The key of package.json a node project declares its settings under. */
export const SETTINGS_KEY: string = 'airness'

/** How a declared exclusion's pattern is matched against a path. */
export type ExclusionKind = 'regex' | 'glob'

/** One exclusion a project declared, and the file role it claims. */
export interface DeclaredExclusion {
  /** Which setting it came from, so a finding can name it. */
  readonly setting: string
  readonly pattern: string
  readonly reason: string
  readonly kind: ExclusionKind
  /** The engine a `javascriptAllowlist` entry declares its files run in, as written; absent elsewhere. */
  readonly runtime?: string
}

/** The settings a gate reads, every field populated. */
export interface Settings {
  /** Directories holding first-party source. Additive to {@link DEFAULT_SOURCE_ROOTS}. */
  readonly sourceRoots: readonly string[]
  /** Paths a framework writes to a fixed shape, which the lint pass relaxes rather than judges. */
  readonly frameworkGlue: readonly string[]
  /** Paths a generator owns, which no rule may judge. */
  readonly generatedArtefacts: readonly string[]
  /** Every exclusion the project declared, honoured or not, so a gate can judge them. */
  readonly declaredExclusions: readonly DeclaredExclusion[]
  /** Path patterns exempt from the hand-written-JavaScript ban. */
  readonly javascriptAllowlist: readonly RolePattern[]
  /** The same roles, each with the engine its files run in, which decides how they are linted. */
  readonly javascriptRuntimes: readonly RuntimeRole[]
}

/**
 * The defaults a layer package contributes to every member that declares it: a stack layer above the node
 * layer, such as the Payload one, knows which files its framework generates and writes to a fixed shape. They
 * are the harness's own, so they need no reason and are not judged as the project's exclusions.
 */
export interface LayerDefaults {
  readonly generatedArtefacts: readonly string[]
  readonly frameworkGlue: readonly string[]
  readonly javascriptRuntimes: readonly RuntimeRole[]
}

/** The directories a project holds first-party source in, whatever else it declares. */
export const DEFAULT_SOURCE_ROOTS: readonly string[] = ['src', 'tests', 'scripts']

/**
 * The setup file a project writes for its own test suite. Named once, because the Biome selection
 * includes it and the ESLint configuration exempts it from a rule aimed at code rather than at process
 * configuration.
 */
export const PROJECT_SETUP_FILE: string = 'vitest.setup.ts'

/** The test configuration a project writes beside its sources. */
export const VITEST_CONFIG_FILE: string = 'vitest.config.mts'

/**
 * The files that hold nothing but a re-export of a harness configuration. The ESLint configuration
 * exempts them from `unicorn/prefer-export-from`, which would otherwise rewrite the shape they must keep.
 */
export const REEXPORT_CONFIG_FILES: readonly string[] = [
  'eslint.config.mjs',
  VITEST_CONFIG_FILE,
  'playwright.config.ts',
]

// `(^|/)` rather than `^`: the patterns name files relative to a member, and a gate may read them from a
// workspace root where the member sits in a subdirectory.
const MEMBER_RELATIVE: string = '(^|/)'

const DEFAULT_JAVASCRIPT_ALLOWLIST: readonly string[] = [
  // ESLint's own flat configuration, which its loader reads as JavaScript.
  String.raw`${MEMBER_RELATIVE}eslint\.config\.mjs$`,
  // Tailwind v4 needs a PostCSS configuration, and Next reads it only as `.mjs` or `.js`.
  String.raw`${MEMBER_RELATIVE}postcss\.config\.(mjs|js)$`,
]

// Every built-in entry is a file a Node.js tool loads.
const DEFAULT_JAVASCRIPT_RUNTIMES: readonly RuntimeRole[] = DEFAULT_JAVASCRIPT_ALLOWLIST.map(
  (role: string): RuntimeRole => ({ role, runtime: 'node' }),
)

const JAVASCRIPT_SETTING: string = 'javascriptAllowlist'

const isTextArray = (raw: unknown): raw is readonly string[] =>
  isArray(raw) && raw.every((entry: unknown): boolean => typeof entry === 'string')

// A bare string is kept with an empty reason rather than dropped silently, so a gate can name the entry
// the project wrote instead of reporting that its exclusions simply stopped applying.
const readExclusionFields = (entry: unknown, setting: string, kind: ExclusionKind): DeclaredExclusion => {
  if (typeof entry === 'string') {
    return { setting, pattern: entry, reason: '', kind }
  }
  if (!isRecord(entry)) {
    return { setting, pattern: '', reason: '', kind }
  }
  return { setting, pattern: asText(entry['pattern']), reason: asText(entry['reason']).trim(), kind }
}

const readExclusion = (entry: unknown, setting: string, kind: ExclusionKind): DeclaredExclusion => {
  const read: DeclaredExclusion = readExclusionFields(entry, setting, kind)
  return setting === JAVASCRIPT_SETTING
    ? { ...read, runtime: isRecord(entry) ? asText(entry['runtime']).trim() : '' }
    : read
}

const asDeclaredExclusions = (raw: unknown, setting: string, kind: ExclusionKind): readonly DeclaredExclusion[] =>
  isArray(raw) ? raw.map((entry: unknown): DeclaredExclusion => readExclusion(entry, setting, kind)) : []

/**
 * Decide whether the harness honours a declared exclusion.
 *
 * An allowlisted file must also say which engine runs it. An entry that does not is not honoured, so its
 * file stays banned rather than being linted for an engine nobody declared.
 * @param entry one declared exclusion.
 * @returns true when the entry states its pattern, its role, and any runtime its setting requires.
 */
export const isHonouredExclusion = (entry: DeclaredExclusion): boolean =>
  entry.pattern.length > 0 &&
  entry.reason.length > 0 &&
  (entry.setting !== JAVASCRIPT_SETTING || isJavascriptRuntime(entry.runtime))

const honoured = (entries: readonly DeclaredExclusion[]): readonly string[] =>
  entries
    .filter((entry: DeclaredExclusion): boolean => isHonouredExclusion(entry))
    .map((entry: DeclaredExclusion): string => entry.pattern)

/**
 * The honoured allowlist entries with the engine each declares.
 * @param entries the declared `javascriptAllowlist` entries.
 * @returns one runtime role per honoured entry.
 */
export const declaredRuntimeRoles = (entries: readonly DeclaredExclusion[]): readonly RuntimeRole[] =>
  entries.flatMap((entry: DeclaredExclusion): readonly RuntimeRole[] =>
    entry.setting === JAVASCRIPT_SETTING && isHonouredExclusion(entry) && isJavascriptRuntime(entry.runtime)
      ? [{ role: entry.pattern, runtime: entry.runtime }]
      : [],
  )

/** The raw `airness` block of a manifest, before any default is applied. */
export const settingsBlock = (packageJson: unknown): Record<string, unknown> =>
  asRecord(asRecord(packageJson)[SETTINGS_KEY])

/**
 * Read the `airness` key of a parsed package.json into fully defaulted settings. A malformed value falls
 * back to the strict default rather than failing the read, so a typo can never widen a rule.
 * @param packageJson the parsed package.json of the node project.
 * @param layers the defaults of every layer package the project declares.
 * @returns the effective settings, with every field populated.
 */
export const readSettings = (packageJson: unknown, layers: readonly LayerDefaults[] = []): Settings => {
  const raw: Record<string, unknown> = settingsBlock(packageJson)
  const javascript: readonly DeclaredExclusion[] = asDeclaredExclusions(
    raw[JAVASCRIPT_SETTING],
    JAVASCRIPT_SETTING,
    'regex',
  )
  const glue: readonly DeclaredExclusion[] = asDeclaredExclusions(raw['frameworkGlue'], 'frameworkGlue', 'glob')
  const generated: readonly DeclaredExclusion[] = asDeclaredExclusions(
    raw['generatedArtefacts'],
    'generatedArtefacts',
    'glob',
  )
  const layerRuntimes: readonly RuntimeRole[] = layers.flatMap(
    (layer: LayerDefaults): readonly RuntimeRole[] => layer.javascriptRuntimes,
  )
  return {
    // Additive: replacing the default would let a project declare `["src"]` and silently drop `tests`
    // and `scripts` from every gate that reads source roots.
    sourceRoots: [
      ...new Set<string>([...DEFAULT_SOURCE_ROOTS, ...(isTextArray(raw['sourceRoots']) ? raw['sourceRoots'] : [])]),
    ],
    frameworkGlue: [
      ...layers.flatMap((layer: LayerDefaults): readonly string[] => layer.frameworkGlue),
      ...honoured(glue),
    ],
    generatedArtefacts: [
      ...layers.flatMap((layer: LayerDefaults): readonly string[] => layer.generatedArtefacts),
      ...honoured(generated),
    ],
    declaredExclusions: [...javascript, ...glue, ...generated],
    javascriptAllowlist: [
      ...DEFAULT_JAVASCRIPT_ALLOWLIST,
      ...layerRuntimes.map((role: RuntimeRole): RolePattern => role.role),
      ...honoured(javascript),
    ],
    javascriptRuntimes: [...DEFAULT_JAVASCRIPT_RUNTIMES, ...layerRuntimes, ...declaredRuntimeRoles(javascript)],
  }
}

/**
 * Report every declared exclusion the harness cannot honour because it states no role.
 * @param entries the exclusions the project declared.
 * @returns one message per entry that states no reason.
 */
export const findConvenienceExclusions = (entries: readonly DeclaredExclusion[]): readonly string[] =>
  entries.flatMap((entry: DeclaredExclusion): readonly string[] =>
    entry.reason.length === 0
      ? [
          `${entry.setting} entry "${entry.pattern}" states no reason; ` +
            'an exclusion is granted by file role, so the role must be written down',
        ]
      : [],
  )

/**
 * Report every allowlist entry that names no engine its file runs in, or one the harness does not lint for.
 * @param entries the exclusions the project declared.
 * @returns one message per entry, listing the runtimes it may declare.
 */
export const findUndeclaredRuntimes = (entries: readonly DeclaredExclusion[]): readonly string[] =>
  entries
    .filter(
      (entry: DeclaredExclusion): boolean =>
        entry.setting === JAVASCRIPT_SETTING && !isJavascriptRuntime(entry.runtime),
    )
    .map(
      (entry: DeclaredExclusion): string =>
        `${entry.setting} entry "${entry.pattern}" ` +
        (entry.runtime === undefined || entry.runtime.length === 0
          ? 'declares no runtime'
          : `declares unknown runtime "${entry.runtime}"`) +
        `; declare the engine its file runs in, one of: ${JAVASCRIPT_RUNTIMES.join(', ')}`,
    )

const reachesAny = (entry: DeclaredExclusion, candidates: readonly string[]): boolean =>
  candidates.some((candidate: string): boolean =>
    entry.kind === 'glob' ? matchesGlob(entry.pattern, candidate) : matchesRole(candidate, [entry.pattern]),
  )

/**
 * Report every declared exclusion that matches none of the paths it could have excluded.
 *
 * An exclusion that reaches nothing leaves the report reading exactly as it would have read without it,
 * so it records a decision nobody can see the effect of, and it outlives the file it was written for.
 * @param entries the exclusions the project declared.
 * @param candidates the paths the corresponding gate would otherwise have judged.
 * @returns one message per entry that reaches nothing.
 */
export const findUnreachedExclusions = (
  entries: readonly DeclaredExclusion[],
  candidates: readonly string[],
): readonly string[] =>
  entries
    .filter((entry: DeclaredExclusion): boolean => entry.pattern.length > 0 && entry.reason.length > 0)
    .filter((entry: DeclaredExclusion): boolean => !reachesAny(entry, candidates))
    .map(
      (entry: DeclaredExclusion): string =>
        `${entry.setting} entry "${entry.pattern}" excludes nothing; ` +
        'it matches no file the gate would have judged, so it records a decision with no effect',
    )

// Which paths an exclusion has to reach is decided by the SETTING it came from, never by how its pattern
// is written: a JavaScript allowlist entry is judged against the JavaScript files alone.
const candidatesFor = (setting: string, tracked: readonly string[]): readonly string[] =>
  setting === JAVASCRIPT_SETTING
    ? tracked.filter((file: string): boolean => hasExtension(file, JAVASCRIPT_EXTENSIONS))
    : tracked

/**
 * Report every declared exclusion that reaches none of the paths ITS OWN setting could have excluded.
 * @param entries the exclusions the project declared, across every setting.
 * @param tracked the member-relative paths of the files a commit would carry.
 * @returns one message per entry that reaches nothing.
 */
export const findUnreachedExclusionsBySetting = (
  entries: readonly DeclaredExclusion[],
  tracked: readonly string[],
): readonly string[] =>
  [...new Set<string>(entries.map((entry: DeclaredExclusion): string => entry.setting))].flatMap(
    (setting: string): readonly string[] =>
      findUnreachedExclusions(
        entries.filter((entry: DeclaredExclusion): boolean => entry.setting === setting),
        candidatesFor(setting, tracked),
      ),
  )
