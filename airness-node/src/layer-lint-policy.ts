// How a stack layer's ESLint blocks join the node layer's: after them, and never weaker. A layer exports its
// configuration as the node layer's blocks followed by its own, the shape its editor reads, and the harness keeps
// the node blocks it loaded itself and admits each further block only when it adds to the verdict. A block that
// turns a node rule off, lowers its severity, replaces its options with ones that do not extend them, ignores
// files for every block or changes how a file is parsed is refused, unless the rule is on the relaxation list
// below for that layer and exactly those files, with the reason it is needed.
import { isDeepStrictEqual } from 'node:util'
import { asRecord, isArray, isRecord } from './json-shapes.js'

/** A node rule a layer may relax, for one set of files, with the reason. */
export interface LayerRelaxation {
  readonly layer: string
  readonly rule: string
  /** The `files` of the block that relaxes it, exactly as the layer writes them. */
  readonly files: readonly string[]
  readonly reason: string
}

/** The relaxations each layer needs, owned by the harness rather than by the layer. */
export const LAYER_RELAXATIONS: readonly LayerRelaxation[] = [
  // Each relaxation names a registered layer package, and goes with that layer's registration.
  {
    layer: '@airness/payload',
    rule: 'unicorn/no-top-level-side-effects',
    files: ['src/payload.config.ts'],
    reason: 'Payload consumes an eagerly built configuration; its boot-time writes belong to payload-source-rules',
  },
  {
    layer: '@airness/payload',
    rule: 'unicorn/filename-case',
    files: ['src/migrations/**/*.ts'],
    reason: '`payload migrate:create` writes `<date>_<time>_<name>.ts`, which is snake case',
  },
  {
    layer: '@airness/payload',
    rule: 'functional/immutable-data',
    files: ['src/hooks/**'],
    reason: "Payload hooks carry a request's state on `req.context`, the one object the hooks block lets them write",
  },
]

/** A layer's configuration, as its export holds it. */
export interface LayerConfig {
  readonly name: string
  readonly config: unknown
}

const ERROR: number = 2
const WARN: number = 1
const SEVERITIES: ReadonlyMap<unknown, number> = new Map<unknown, number>([
  ['error', ERROR],
  [ERROR, ERROR],
  ['warn', WARN],
  [WARN, WARN],
  ['off', 0],
  [0, 0],
])

// A block may scope itself, name itself, mount the plugins its rules need and set rules, and nothing else.
const ALLOWED_KEYS: ReadonlySet<string> = new Set(['files', 'ignores', 'name', 'plugins', 'rules'])

const severityOf = (setting: unknown): number | undefined => SEVERITIES.get(isArray(setting) ? setting[0] : setting)

const optionsOf = (setting: unknown): readonly unknown[] => (isArray(setting) ? setting.slice(1) : [])

const rulesOf = (block: unknown): Readonly<Record<string, unknown>> => asRecord(asRecord(block)['rules'])

// The node settings a layer setting is held to: the last one that applies to every file, as ESLint applies it,
// or, for a rule the node layer sets only for some files, every one of them.
const referencesOf = (node: readonly unknown[], rule: string): readonly unknown[] => {
  const settings: readonly (readonly [unknown, unknown])[] = node.flatMap(
    (block: unknown): readonly (readonly [unknown, unknown])[] =>
      Object.hasOwn(rulesOf(block), rule) ? [[block, rulesOf(block)[rule]]] : [],
  )
  const global: readonly unknown[] = settings
    .filter(([block]: readonly [unknown, unknown]): boolean => asRecord(block)['files'] === undefined)
    .map(([, setting]: readonly [unknown, unknown]): unknown => setting)
  return global.length > 0
    ? global.slice(-1)
    : settings.map(([, setting]: readonly [unknown, unknown]): unknown => setting)
}

// Options extend another set when they are the same, or keep every entry of a non-empty list and add more, as a
// list of restricted selectors or properties is extended.
const isExtendingOptions = (base: readonly unknown[], layer: readonly unknown[]): boolean =>
  isDeepStrictEqual(base, layer) ||
  (base.length > 0 &&
    base.every((entry: unknown): boolean =>
      layer.some((candidate: unknown): boolean => isDeepStrictEqual(entry, candidate)),
    ))

const isWeaker = (setting: unknown, references: readonly unknown[]): boolean =>
  severityOf(setting) !== ERROR ||
  references.some((reference: unknown): boolean => !isExtendingOptions(optionsOf(reference), optionsOf(setting)))

const isRelaxed = (layer: string, rule: string, block: unknown, relaxations: readonly LayerRelaxation[]): boolean =>
  relaxations.some(
    (relaxation: LayerRelaxation): boolean =>
      relaxation.layer === layer &&
      relaxation.rule === rule &&
      isDeepStrictEqual([...relaxation.files], asRecord(block)['files']),
  )

/** What one layer block is judged against. */
interface Judgement {
  readonly layer: string
  readonly node: readonly unknown[]
  readonly relaxations: readonly LayerRelaxation[]
}

const blockProblems = ({ layer, node, relaxations }: Judgement, block: unknown, index: number): readonly string[] => {
  const where: string = `block ${String(index)} of the stack layer ${layer}`
  if (!isRecord(block)) {
    return [`${where} is no configuration block`]
  }
  const keys: readonly string[] = Object.keys(block)
  const shape: readonly string[] = keys
    .filter((key: string): boolean => !ALLOWED_KEYS.has(key))
    .map((key: string): string => `${where} sets ${key}, which only the node layer sets`)
  const ignoresAll: readonly string[] =
    Object.hasOwn(block, 'ignores') && keys.every((key: string): boolean => key === 'ignores' || key === 'name')
      ? [`${where} ignores files for every block, the node layer's included`]
      : []
  const rules: readonly string[] = Object.entries(rulesOf(block))
    .filter(
      ([rule, setting]: readonly [string, unknown]): boolean =>
        node.some((nodeBlock: unknown): boolean => Object.hasOwn(rulesOf(nodeBlock), rule)) &&
        isWeaker(setting, referencesOf(node, rule)) &&
        !isRelaxed(layer, rule, block, relaxations),
    )
    .map(
      ([rule]: readonly [string, unknown]): string =>
        `${where} weakens the node rule ${rule}: it turns it off, lowers it below error or replaces its options ` +
        'with ones that do not extend them, and the harness lists no relaxation of it for those files',
    )
  return [...shape, ...ignoresAll, ...rules]
}

const layerProblems = (
  node: readonly unknown[],
  layer: LayerConfig,
  relaxations: readonly LayerRelaxation[],
): readonly string[] => {
  const blocks: readonly unknown[] = isArray(layer.config) ? layer.config : []
  const isExtension: boolean =
    blocks.length >= node.length && node.every((block: unknown, index: number): boolean => blocks[index] === block)
  if (!isExtension) {
    return [
      `the ESLint configuration of the stack layer ${layer.name} does not start with the node layer's blocks; ` +
        'export [...nodeConfig, ...layerBlocks], nodeConfig imported from @airness/node/eslint',
    ]
  }
  return blocks
    .slice(node.length)
    .flatMap((block: unknown, index: number): readonly string[] =>
      blockProblems({ layer: layer.name, node, relaxations }, block, index),
    )
}

/**
 * The node layer's blocks, then each layer's own, every layer block admitted only when it adds to the verdict.
 * @param node the node layer's configuration, as the harness loaded it.
 * @param layers each declared layer's exported configuration.
 * @param relaxations the node rules a layer may relax, and where.
 * @returns the composed configuration.
 * @throws {Error} naming every layer block that would weaken the node layer's verdict.
 */
export const composeLayered = (
  node: readonly unknown[],
  layers: readonly LayerConfig[],
  relaxations: readonly LayerRelaxation[] = LAYER_RELAXATIONS,
): readonly unknown[] => {
  const problems: readonly string[] = layers.flatMap((layer: LayerConfig): readonly string[] =>
    layerProblems(node, layer, relaxations),
  )
  if (problems.length > 0) {
    throw new Error(`a stack layer's ESLint configuration is refused:\n${problems.join('\n')}`)
  }
  return [
    ...node,
    ...layers.flatMap((layer: LayerConfig): readonly unknown[] =>
      isArray(layer.config) ? layer.config.slice(node.length) : [],
    ),
  ]
}
