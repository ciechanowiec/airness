import type { Viewport } from './browser-findings.js'
import { type AuxiliaryServer, browserSettings } from './browser-settings.js'
// The facts a node project may declare under the `airness` key of its package.json. Every field is
// optional and every default is strict, so a project that declares nothing still receives the full
// contract. The one exception is `productionDeadCode`, a stricter reading of the dead-code rule that a project
// opts into while the harness's own packages and its consumers cannot yet all meet it.
//
// The set is deliberately small: where first-party source lives, which files a generator owns, which
// files are framework glue, and which hand-written JavaScript a tool forces on the project. There is no
// field that turns a gate off. An exclusion is an exclusion by file role, so an entry that names no role
// is not honoured, which leaves the gate stricter rather than looser.
import {
  hasExtension,
  JAVASCRIPT_EXTENSIONS,
  matchesGlob,
  matchesRole,
  normalisedSourceRoot,
  type RolePattern,
} from './file-roles.js'
import { type InvocationSettings, invocationSettings } from './invocation-settings.js'
import { isJavascriptRuntime, JAVASCRIPT_RUNTIMES, type RuntimeRole } from './javascript-runtimes.js'
import { asRecord, asText, isArray, isRecord } from './json-shapes.js'
import { isSubtreePrefix } from './route-exclusions.js'

/** The key of package.json a node project declares its settings under. */
export const SETTINGS_KEY: string = 'airness'

/** How a declared exclusion's pattern is matched against a path. */
export type ExclusionKind = 'regex' | 'glob' | 'directory' | 'route'

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
export interface Settings extends InvocationSettings {
  /** The verification clock, or real for time-dependent applications. */
  readonly clock: string
  /** Servers started before the application, such as a sibling CMS or Storybook. */
  readonly auxiliaryServers: readonly AuxiliaryServer[]
  /** Directories holding first-party source. Additive to {@link DEFAULT_SOURCE_ROOTS}. */
  readonly sourceRoots: readonly string[]
  /** Paths a framework writes to a fixed shape, which the lint pass relaxes rather than judges. */
  readonly frameworkGlue: readonly string[]
  /** Paths a generator owns, which no rule may judge. */
  readonly generatedArtefacts: readonly string[]
  /** Paths the coverage floor does not measure, each by a role the project names. */
  readonly coverageExclude: readonly string[]
  /** Every exclusion the project declared, honoured or not, so a gate can judge them. */
  readonly declaredExclusions: readonly DeclaredExclusion[]
  /** Path patterns exempt from the hand-written-JavaScript ban. */
  readonly javascriptAllowlist: readonly RolePattern[]
  /** The same roles, each with the engine its files run in, which decides how they are linted. */
  readonly javascriptRuntimes: readonly RuntimeRole[]
  /**
   * Directories forming the pure-logic floor, which may depend on nothing else under a source root. Empty
   * unless the project or a layer package declares one.
   */
  readonly pureLogicRoots: readonly string[]
  /**
   * The `package.json` script that serves a browser application that is not a Next.js application, which the
   * end-to-end suite starts. Undefined when the project declares none.
   */
  readonly serve: string | undefined
  /**
   * The origin the end-to-end suite drives outside verification. A verification run keeps its scheme, host and
   * path and serves on a port chosen for the run.
   */
  readonly serverUrl: string
  /** Environment variables the application reads its own origin from, which verification sets to the served one. */
  readonly serverUrlVariables: readonly string[]
  /** The gzipped client JavaScript ceiling of a Next.js application, which a project may only lower. */
  readonly bundleBudgetBytes: number
  /** Route prefixes the accessibility crawl does not follow. */
  readonly accessibilitySkipRoutes: readonly string[]
  /** The prefixes the project itself declared, apart from a layer's. */
  readonly accessibilityCustomSkipRoutes: readonly string[]
  /** How many routes the accessibility crawl may discover before it fails; a project may only lower it. */
  readonly accessibilityRouteBudget: number
  /** The smallest gap, in CSS pixels, between a boxed element and its sibling; a project may only raise it. */
  readonly layoutMinimumGap: number
  /** Viewports the layout scan measures beside the ones the harness requires. */
  readonly layoutViewports: readonly Viewport[]
  /**
   * Whether the dead-code gate also judges production code by production use alone, so an export or a production
   * file that only tests and tooling reach fails. Off unless the project declares `true`: the stricter reading,
   * which a project opts into and can only tighten the gate.
   */
  readonly productionDeadCode: boolean
  /** Files the production run of the dead-code gate counts as production entry points, beyond those knip reads. */
  readonly productionEntries: readonly string[]
  /**
   * The knip plugins the production run of the dead-code gate leaves out, because each reads one of
   * {@link Settings.productionEntries} as a tool's configuration, which knip's production mode passes over.
   */
  readonly knipConfigPlugins: readonly string[]
}

/** The origin the end-to-end suite drives when the project declares none. */
export const DEFAULT_SERVER_URL: string = 'http://localhost:3000'

const BYTES_PER_KIB: number = 1024
const BUNDLE_BUDGET_KIB: number = 1100

/** The gzipped client JavaScript ceiling, which a project may lower and never raise. */
export const BUNDLE_BUDGET_BYTES: number = BUNDLE_BUDGET_KIB * BYTES_PER_KIB

/** How many routes the accessibility crawl may discover, which a project may lower and never raise. */
export const ACCESSIBILITY_ROUTE_BUDGET: number = 250

/** The smallest gap between a boxed element and its sibling, which a project may raise and never lower. */
export const LAYOUT_MINIMUM_GAP: number = 4

/**
 * The defaults a layer package contributes to every member that declares it: a stack layer above the node
 * layer knows which files its framework generates and writes to a fixed shape. They
 * are the harness's own, so they need no reason and are not judged as the project's exclusions.
 */
export interface LayerDefaults {
  readonly generatedArtefacts: readonly string[]
  readonly frameworkGlue: readonly string[]
  readonly javascriptRuntimes: readonly RuntimeRole[]
  /** The pure-logic floor the layer's framework lays out, such as an access-rule directory. */
  readonly pureLogicRoots?: readonly string[]
  /** Route prefixes the layer's framework serves outside the public site, such as an admin panel. */
  readonly accessibilitySkipRoutes?: readonly string[]
  /**
   * Files the layer's framework runs in production of its own accord, such as the configuration an application
   * boots from, which no import from the package's interface reaches.
   */
  readonly productionEntries?: readonly string[]
  /** The knip plugins that read one of {@link LayerDefaults.productionEntries} as a tool's configuration. */
  readonly knipConfigPlugins?: readonly string[]
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
  return {
    setting,
    pattern: asText(entry['pattern']),
    reason: asText(entry['reason']).trim(),
    kind,
  }
}

const readExclusion = (entry: unknown, setting: string, kind: ExclusionKind): DeclaredExclusion => {
  const read: DeclaredExclusion = readExclusionFields(entry, setting, kind)
  return setting === JAVASCRIPT_SETTING
    ? {
        ...read,
        runtime: isRecord(entry) ? asText(entry['runtime']).trim() : '',
      }
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

// A skipped route prefix is honoured only where it names a subtree: `/` would switch the sweep off.
const honouredRoutes = (routes: readonly DeclaredExclusion[]): readonly string[] =>
  honoured(routes).filter((prefix: string): boolean => isSubtreePrefix(prefix))

const withoutTrailingSlash = (directory: string): string =>
  directory.endsWith('/') ? withoutTrailingSlash(directory.slice(0, -1)) : directory

// The pure-logic floor: every layer's, then every directory the project declares with a reason.
const floorOf = (floor: readonly DeclaredExclusion[], layers: readonly LayerDefaults[]): readonly string[] => [
  ...new Set<string>([
    ...layers.flatMap((layer: LayerDefaults): readonly string[] => layer.pureLogicRoots ?? []),
    ...honoured(floor).map((directory: string): string => withoutTrailingSlash(directory)),
  ]),
]

const asPositiveInteger = (raw: unknown, fallback: number): number =>
  typeof raw === 'number' && Number.isSafeInteger(raw) && raw > 0 ? raw : fallback

// A ceiling a project may only lower: a larger or malformed value leaves the harness's own.
const clampedCeiling = (raw: unknown, shipped: number): number => Math.min(shipped, asPositiveInteger(raw, shipped))

const asLayoutViewports = (raw: unknown): readonly Viewport[] =>
  isArray(raw)
    ? raw.flatMap((entry: unknown): readonly Viewport[] => {
        const record: Record<string, unknown> = asRecord(entry)
        const width: number = asPositiveInteger(record['width'], 0)
        const height: number = asPositiveInteger(record['height'], 0)
        return width > 0 && height > 0 ? [{ width, height }] : []
      })
    : []

// What a browser application declares: how it is served and where it answers, and the one-directional
// limits of the accessibility and layout scans. None of them reaches a rule; each can only narrow a scan's
// reach in the stricter direction, or say where the application is.
const appSettings = (
  raw: Record<string, unknown>,
  layers: readonly LayerDefaults[],
  customRoutes: readonly string[],
): Pick<
  Settings,
  | 'serve'
  | 'serverUrl'
  | 'bundleBudgetBytes'
  | 'accessibilitySkipRoutes'
  | 'accessibilityCustomSkipRoutes'
  | 'accessibilityRouteBudget'
  | 'layoutMinimumGap'
  | 'layoutViewports'
> => {
  const serve: string = asText(raw['serve']).trim()
  const serverUrl: string = asText(raw['serverUrl']).trim()
  return {
    serve: serve.length > 0 ? serve : undefined,
    serverUrl: serverUrl.length > 0 ? serverUrl : DEFAULT_SERVER_URL,
    bundleBudgetBytes: clampedCeiling(raw['bundleBudgetBytes'], BUNDLE_BUDGET_BYTES),
    accessibilitySkipRoutes: [
      ...new Set<string>([
        ...layers.flatMap((layer: LayerDefaults): readonly string[] => layer.accessibilitySkipRoutes ?? []),
        ...customRoutes,
      ]),
    ],
    accessibilityCustomSkipRoutes: customRoutes,
    accessibilityRouteBudget: clampedCeiling(raw['accessibilityRouteBudget'], ACCESSIBILITY_ROUTE_BUDGET),
    layoutMinimumGap: Math.max(LAYOUT_MINIMUM_GAP, asPositiveInteger(raw['layoutMinimumGap'], LAYOUT_MINIMUM_GAP)),
    layoutViewports: asLayoutViewports(raw['layoutViewports']),
  }
}

// What the dead-code gate reads: whether the project opts into the production run, and the production entry points
// and the knip plugins each layer's framework adds to it.
const deadCodeSettings = (
  raw: Readonly<Record<string, unknown>>,
  layers: readonly LayerDefaults[],
): Pick<Settings, 'productionDeadCode' | 'productionEntries' | 'knipConfigPlugins'> => ({
  productionDeadCode: raw['productionDeadCode'] === true,
  productionEntries: layers.flatMap((layer: LayerDefaults): readonly string[] => layer.productionEntries ?? []),
  knipConfigPlugins: [
    ...new Set<string>(layers.flatMap((layer: LayerDefaults): readonly string[] => layer.knipConfigPlugins ?? [])),
  ],
})

/** The raw `airness` block of a manifest, before any default is applied. */
export const settingsBlock = (packageJson: unknown): Record<string, unknown> => {
  const declared: unknown = asRecord(packageJson)[SETTINGS_KEY]
  if (declared !== undefined && (!isRecord(declared) || isArray(declared))) {
    throw new Error('package.json airness must be an object; process isolation cannot be silently discarded')
  }
  return asRecord(declared)
}

// A declared root in the one spelling every gate matches against: `lib/` and `./lib` are `lib`, and `.` is the
// whole member. A root that escapes the member stays as declared, so each gate that selects files by root
// refuses it with the reason, rather than the settings quietly dropping a directory the project means to govern.
const declaredRoot = (root: string): string => {
  try {
    return normalisedSourceRoot(root)
  } catch {
    return root
  }
}

// The defaults and the declared roots, each once, in the spelling the gates match.
const sourceRootsOf = (declared: unknown): readonly string[] => [
  ...new Set<string>([
    ...DEFAULT_SOURCE_ROOTS,
    ...(isTextArray(declared) ? declared.map((root: string): string => declaredRoot(root)) : []),
  ]),
]

/**
 * Read the `airness` key of a parsed package.json into fully defaulted settings. A malformed value falls
 * back to the strict policy default. Malformed process declarations are refused, because ignoring one
 * could run an application without the isolation its author intended.
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
  const coverage: readonly DeclaredExclusion[] = asDeclaredExclusions(raw['coverageExclude'], 'coverageExclude', 'glob')
  const floor: readonly DeclaredExclusion[] = asDeclaredExclusions(raw['pureLogicRoots'], 'pureLogicRoots', 'directory')
  const routes: readonly DeclaredExclusion[] = asDeclaredExclusions(
    raw['accessibilitySkipRoutes'],
    'accessibilitySkipRoutes',
    'route',
  )
  const layerRuntimes: readonly RuntimeRole[] = layers.flatMap(
    (layer: LayerDefaults): readonly RuntimeRole[] => layer.javascriptRuntimes,
  )
  return {
    ...invocationSettings(raw),
    // Additive: replacing the default would let a project declare `["src"]` and silently drop `tests`
    // and `scripts` from every gate that reads source roots.
    sourceRoots: sourceRootsOf(raw['sourceRoots']),
    frameworkGlue: [
      ...layers.flatMap((layer: LayerDefaults): readonly string[] => layer.frameworkGlue),
      ...honoured(glue),
    ],
    generatedArtefacts: [
      ...layers.flatMap((layer: LayerDefaults): readonly string[] => layer.generatedArtefacts),
      ...honoured(generated),
    ],
    coverageExclude: honoured(coverage),
    declaredExclusions: [...javascript, ...glue, ...generated, ...coverage, ...floor, ...routes],
    javascriptAllowlist: [
      ...DEFAULT_JAVASCRIPT_ALLOWLIST,
      ...layerRuntimes.map((role: RuntimeRole): RolePattern => role.role),
      ...honoured(javascript),
    ],
    javascriptRuntimes: [...DEFAULT_JAVASCRIPT_RUNTIMES, ...layerRuntimes, ...declaredRuntimeRoles(javascript)],
    pureLogicRoots: floorOf(floor, layers),
    ...deadCodeSettings(raw, layers),
    ...appSettings(raw, layers, honouredRoutes(routes)),
    ...browserSettings(raw),
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

const reachesCandidate = (entry: DeclaredExclusion, candidate: string): boolean => {
  if (entry.kind === 'glob') {
    return matchesGlob(entry.pattern, candidate)
  }
  if (entry.kind === 'directory') {
    return candidate.startsWith(`${withoutTrailingSlash(entry.pattern)}/`)
  }
  return matchesRole(candidate, [entry.pattern])
}

// A route prefix names a URL the running application serves, which no file in the tree can show, so its
// reach is not judged here.
const reachesAny = (entry: DeclaredExclusion, candidates: readonly string[]): boolean =>
  entry.kind === 'route' || candidates.some((candidate: string): boolean => reachesCandidate(entry, candidate))

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
