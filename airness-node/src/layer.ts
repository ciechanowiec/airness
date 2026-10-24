// The defaults a stack layer above the node layer contributes. Only the layer packages named here are read,
// so a project cannot widen its own exclusions by depending on a package that claims to be a layer.
import { createRequire } from 'node:module'
import path from 'node:path'
import { isJavascriptRuntime, type RuntimeRole } from './javascript-runtimes.js'
import { asRecord, asText, declaredDependencies, isArray } from './json-shapes.js'
import type { LayerDefaults } from './settings.js'

/** The npm packages that are stack layers above this one: the Payload layer. */
export const LAYER_PACKAGES: readonly string[] = ['ploaness']

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

/**
 * Read the `airness.layer` block of a layer package's manifest.
 * @param manifest the layer package's parsed package.json.
 * @returns the defaults it contributes; an absent or malformed field contributes nothing.
 */
export const layerDefaultsOf = (manifest: unknown): LayerDefaults => {
  const block: Record<string, unknown> = asRecord(asRecord(asRecord(manifest)['airness'])['layer'])
  return {
    generatedArtefacts: texts(block['generatedArtefacts']),
    frameworkGlue: texts(block['frameworkGlue']),
    javascriptRuntimes: runtimes(block['javascriptAllowlist']),
    pureLogicRoots: texts(block['pureLogicRoots']),
    accessibilitySkipRoutes: texts(block['accessibilitySkipRoutes']),
  }
}

/**
 * The defaults of every layer package a member declares, read from the copy the member resolves.
 * @param directory the member's directory.
 * @param packageJson the member's parsed package.json.
 * @param readManifest how a manifest file is read.
 * @returns one entry per declared layer package.
 */
export const layerDefaultsFor = (
  directory: string,
  packageJson: unknown,
  readManifest: (file: string) => unknown,
): readonly LayerDefaults[] => {
  const declared: Record<string, string> = declaredDependencies(packageJson)
  const resolveFrom: NodeJS.Require = createRequire(path.join(directory, 'package.json'))
  return LAYER_PACKAGES.filter((name: string): boolean => Object.hasOwn(declared, name)).map(
    (name: string): LayerDefaults => layerDefaultsOf(readManifest(resolveFrom.resolve(`${name}/package.json`))),
  )
}
