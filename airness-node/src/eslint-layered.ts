// The ESLint configuration the eslint gate runs and every editor reads: the node layer's, then the blocks each
// declared stack layer adds, composed by the harness rather than handed over by the layer. It lives apart from
// `eslint.ts` because a layer's export imports `@airness/node/eslint`; loading the layer from there would be an
// import cycle, and loading both from here is not.
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import nodeConfig from './eslint.js'
import { asRecord } from './json-shapes.js'
import { LAYER_NAMES, type LayerPackage, layerEslintConfigOf, layerPackagesFor } from './layer.js'
import { composeLayered, type LayerConfig } from './layer-lint-policy.js'
import { readJson } from './project.js'
import { projectDirectory } from './project-settings.js'

/**
 * The node layer's configuration, then the blocks of every layer package the project declares.
 * @param directory the project directory.
 * @param layers the packages read as layers, the registered ones unless a caller names others.
 * @returns the composed configuration.
 * @throws {Error} naming every layer block that would weaken the node layer's verdict.
 */
export const composedConfig = async (
  directory: string,
  layers: readonly string[] = LAYER_NAMES,
): Promise<readonly unknown[]> => {
  // A project whose package.json is absent or unparseable declares no layer, which leaves the node layer's
  // configuration.
  const manifest: unknown = readJson(path.join(directory, 'package.json'))
  const declared: readonly (readonly [string, string])[] = layerPackagesFor(
    directory,
    manifest,
    readJson,
    layers,
  ).flatMap((layer: LayerPackage): readonly (readonly [string, string])[] => {
    const file: string | undefined = layerEslintConfigOf(layer, directory)
    return file === undefined ? [] : [[layer.name, file]]
  })
  const configs: readonly LayerConfig[] = await Promise.all(
    declared.map(
      async ([name, file]: readonly [string, string]): Promise<LayerConfig> => ({
        name,
        config: asRecord(await import(pathToFileURL(file).href))['default'],
      }),
    ),
  )
  return composeLayered(nodeConfig, configs)
}

const config: readonly unknown[] = await composedConfig(projectDirectory)

export default config
