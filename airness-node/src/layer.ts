// The defaults a stack layer above the node layer contributes, and the two seams through which it joins the
// node layer's gates: the end-to-end specifications it ships and the ESLint blocks it adds to the node layer's.
// Only the layer packages named here are read, only from the copy the member resolves, and never the member
// itself, so a project cannot widen its own exclusions or reach a gate's configuration by depending on a package
// that claims to be a layer, or by being one, and no file of the project can name either seam.
import { existsSync, readdirSync, realpathSync } from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
import { isJavascriptRuntime, type RuntimeRole } from './javascript-runtimes.js'
import { asRecord, asText, declaredDependencies, isArray } from './json-shapes.js'
import type { LayerDefaults } from './settings.js'

/**
 * The npm packages that are stack layers above this one, each with the one Maven module that builds it, as
 * `groupId:artifactId`. A deliberate trust allowlist: only a package named here is read as a layer. Each layer is
 * one line of its own, so a layer is registered and removed by that line alone.
 */
export const LAYER_PACKAGES: Readonly<Record<string, string>> = {
  // One line per stack layer: its npm package, then the Maven module that builds it.
  '@airness/payload': 'eu.ciechanowiec:airness-payload-node',
}

/** The registered layer package names: what every seam reads as layers unless a caller names others. */
export const LAYER_NAMES: readonly string[] = Object.keys(LAYER_PACKAGES)

const texts = (raw: unknown): readonly string[] =>
  isArray(raw) ? raw.filter((entry: unknown): entry is string => typeof entry === 'string') : []

const runtimes = (raw: unknown): readonly RuntimeRole[] =>
  isArray(raw)
    ? raw.flatMap((entry: unknown): readonly RuntimeRole[] => {
        const record: Record<string, unknown> = asRecord(entry)
        const pattern: string = asText(record['pattern'])
        const runtime: unknown = record['runtime']
        return pattern.length > 0 && isJavascriptRuntime(runtime) ? [{ role: pattern, runtime }] : []
      })
    : []

// The `airness.layer` block of a layer package's manifest, empty when it has none.
const layerBlock = (manifest: unknown): Record<string, unknown> => {
  const airness: Record<string, unknown> = asRecord(asRecord(manifest)['airness'])
  return asRecord(airness['layer'])
}

/**
 * Read the `airness.layer` block of a layer package's manifest.
 * @param manifest the layer package's parsed package.json.
 * @returns the defaults it contributes; an absent or malformed field contributes nothing.
 */
export const layerDefaultsOf = (manifest: unknown): LayerDefaults => {
  const block: Record<string, unknown> = layerBlock(manifest)
  return {
    generatedArtefacts: texts(block['generatedArtefacts']),
    frameworkGlue: texts(block['frameworkGlue']),
    javascriptRuntimes: runtimes(block['javascriptAllowlist']),
    pureLogicRoots: texts(block['pureLogicRoots']),
    accessibilitySkipRoutes: texts(block['accessibilitySkipRoutes']),
  }
}

/** A layer package a member declares, as the member resolves it. */
export interface LayerPackage {
  readonly name: string
  /** The resolved package's own directory. */
  readonly directory: string
  /** Its parsed package.json. */
  readonly manifest: unknown
}

/**
 * Every layer package a member declares, read from the copy the member resolves. A member is never its own
 * layer: a declared layer that is the member itself, by name or by where it resolves, is passed over, so a
 * project cannot grant itself a layer's defaults or seams by naming itself after one and linking to itself.
 * @param directory the member's directory.
 * @param packageJson the member's parsed package.json.
 * @param readManifest how a manifest file is read.
 * @param layers the packages read as layers, the registered ones unless a caller names others.
 * @returns one entry per declared layer package.
 */
export const layerPackagesFor = (
  directory: string,
  packageJson: unknown,
  readManifest: (file: string) => unknown,
  layers: readonly string[] = LAYER_NAMES,
): readonly LayerPackage[] => {
  const declared: Record<string, string> = declaredDependencies(packageJson)
  const ownName: string = asText(asRecord(packageJson)['name'])
  const resolveFrom: NodeJS.Require = createRequire(path.join(directory, 'package.json'))
  return layers
    .filter((name: string): boolean => Object.hasOwn(declared, name) && name !== ownName)
    .map((name: string): LayerPackage => {
      const manifestFile: string = resolveFrom.resolve(`${name}/package.json`)
      return {
        name,
        directory: path.dirname(manifestFile),
        manifest: readManifest(manifestFile),
      }
    })
    .filter((layer: LayerPackage): boolean => realpathSync(layer.directory) !== realpathSync(directory))
}

/**
 * The defaults of every layer package a member declares, read from the copy the member resolves.
 * @param directory the member's directory.
 * @param packageJson the member's parsed package.json.
 * @param readManifest how a manifest file is read.
 * @param layers the packages read as layers, the registered ones unless a caller names others.
 * @returns one entry per declared layer package.
 */
export const layerDefaultsFor = (
  directory: string,
  packageJson: unknown,
  readManifest: (file: string) => unknown,
  layers: readonly string[] = LAYER_NAMES,
): readonly LayerDefaults[] =>
  layerPackagesFor(directory, packageJson, readManifest, layers).map(
    (layer: LayerPackage): LayerDefaults => layerDefaultsOf(layer.manifest),
  )

const testDependenciesOf = (layer: LayerPackage): readonly string[] => {
  const value: unknown = layerBlock(layer.manifest)['vitestInlineDependencies']
  if (value === undefined) {
    return []
  }
  if (
    !(isArray(value) && value.every((entry: unknown): boolean => typeof entry === 'string' && entry.trim().length > 0))
  ) {
    throw new TypeError(
      `The stack layer ${layer.name} must name vitestInlineDependencies as an array of dependency names`,
    )
  }
  return texts(value)
}

/** Dependencies a trusted framework layer needs Vite to transform, including their stylesheet imports. */
export const layerTestDependenciesFor = (
  directory: string,
  packageJson: unknown,
  readManifest: (file: string) => unknown,
  layers: readonly string[] = LAYER_NAMES,
): readonly string[] => [
  ...new Set(
    layerPackagesFor(directory, packageJson, readManifest, layers).flatMap((layer: LayerPackage): readonly string[] =>
      testDependenciesOf(layer),
    ),
  ),
]

// A seam the layer's manifest names: undefined when it names none, and refused, never passed over, when it
// names one in a shape the seam cannot use, since a harness seam that falls back silently fails open.
const layerField = (layer: LayerPackage, key: string, isValid: (value: string) => boolean): string | undefined => {
  const value: unknown = layerBlock(layer.manifest)[key]
  if (value === undefined) {
    return undefined
  }
  if (typeof value !== 'string' || !isValid(value)) {
    throw new Error(
      `the stack layer ${layer.name} names airness.layer.${key} as ${JSON.stringify(value)}, which is not a ` +
        (key === 'e2eSpecs' ? 'directory inside the package' : "package export such as './eslint'"),
    )
  }
  return value
}

// A path inside the package, written relative to it: no absolute path and no step out of the package.
const isInsidePackage = (relative: string): boolean =>
  relative.length > 0 &&
  !path.isAbsolute(relative) &&
  !relative.includes('\\') &&
  relative.split('/').every((segment: string): boolean => segment !== '..')

/** The end-to-end specifications a layer ships: a Playwright project of its own over one of its directories. */
export interface LayerSpecifications {
  /** The layer package, which names the Playwright project. */
  readonly name: string
  /** The directory whose compiled `*.e2e.spec.js` files the project runs. */
  readonly directory: string
}

/**
 * The directory of compiled end-to-end specifications a layer ships, named by `airness.layer.e2eSpecs` as a
 * path inside the package, such as `dist`.
 * @param layer the layer package.
 * @returns the specifications, or undefined when the layer names none.
 * @throws {Error} when the layer names a value that is no path inside the package.
 */
export const layerSpecificationsOf = (layer: LayerPackage): LayerSpecifications | undefined => {
  const relative: string | undefined = layerField(layer, 'e2eSpecs', isInsidePackage)
  return relative === undefined ? undefined : { name: layer.name, directory: path.join(layer.directory, relative) }
}

/** The compiled end-to-end specifications a harness ships, which a project never holds a copy of. */
export const SHIPPED_SPECS: RegExp = /\.e2e\.spec\.js$/u

const SPEC_SUFFIX: string = '.e2e.spec.js'

const hasSpecifications = (directory: string): boolean =>
  existsSync(directory) &&
  readdirSync(directory, { recursive: true, encoding: 'utf8' }).some((file: string): boolean =>
    file.endsWith(SPEC_SUFFIX),
  )

/** A Playwright project over the specifications one layer ships. */
export interface LayerProject {
  readonly name: string
  readonly testDir: string
  readonly testMatch: RegExp
  readonly use: Readonly<Record<string, unknown>>
}

/**
 * The Playwright projects of the specifications each declared stack layer ships, which the shipped Playwright
 * configuration runs beside the node layer's own. Playwright runs a project whose directory is missing or holds
 * no specification as zero tests and passes, so a declared directory that holds none is refused here instead.
 * @param layers the layer packages the member declares, as {@link layerPackagesFor} reads them.
 * @param use the browser every project drives.
 * @returns one project per layer that ships specifications.
 * @throws {Error} when a layer names an unusable value or a directory without specifications.
 */
export const layerProjectsFor = (
  layers: readonly LayerPackage[],
  use: Readonly<Record<string, unknown>>,
): readonly LayerProject[] =>
  layers.flatMap((layer: LayerPackage): readonly LayerProject[] => {
    const specifications: LayerSpecifications | undefined = layerSpecificationsOf(layer)
    if (specifications === undefined) {
      return []
    }
    if (!hasSpecifications(specifications.directory)) {
      throw new Error(
        `the stack layer ${layer.name} names ${specifications.directory} for its end-to-end specifications, ` +
          'which holds no *.e2e.spec.js file; the layer is broken, so its specifications would silently not run',
      )
    }
    return [
      {
        name: specifications.name,
        testDir: specifications.directory,
        testMatch: SHIPPED_SPECS,
        use,
      },
    ]
  })

const SUBPATH_PREFIX: string = './'

// An export of the package itself, such as `./eslint`, never a file path or a module of another package.
const EXPORT_SUBPATH: RegExp = /^\.\/[\w-]+(?:\/[\w.-]+)*$/u

const isExportSubpath = (subpath: string): boolean =>
  EXPORT_SUBPATH.test(subpath) && isInsidePackage(subpath.slice(SUBPATH_PREFIX.length))

/** Resolve the browser adapter of a trusted layer, never a project-owned module. */
export const layerBrowserConfigOf = (layer: LayerPackage, directory: string): string | undefined => {
  const subpath: string | undefined = layerField(layer, 'browser', isExportSubpath)
  return subpath === undefined
    ? undefined
    : createRequire(path.join(directory, 'package.json')).resolve(
        `${layer.name}/${subpath.slice(SUBPATH_PREFIX.length)}`,
      )
}

/**
 * The ESLint configuration a layer adds to the node layer's, named by `airness.layer.eslint` as one of the
 * package's own exports, such as `./eslint`. The harness composes it with the node layer's in
 * `@airness/node/eslint-layered`, which the eslint gate runs and the layer's managed `eslint.config.mjs` gives an
 * editor.
 * @param layer the layer package.
 * @param directory the member's directory, from which the export is resolved.
 * @returns the configuration's file, or undefined when the layer names none.
 * @throws {Error} when the layer names a value that is no export of its own, or an export it does not have.
 */
export const layerEslintConfigOf = (layer: LayerPackage, directory: string): string | undefined => {
  const subpath: string | undefined = layerField(layer, 'eslint', isExportSubpath)
  return subpath === undefined
    ? undefined
    : createRequire(path.join(directory, 'package.json')).resolve(
        `${layer.name}/${subpath.slice(SUBPATH_PREFIX.length)}`,
      )
}
