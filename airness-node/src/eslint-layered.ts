// The ESLint configuration the eslint gate runs and every editor reads: the node layer's, then the blocks each
// declared stack layer adds, composed by the harness rather than handed over by the layer. It lives apart from
// `eslint.ts` because a layer's export imports `@airness/node/eslint`; loading the layer from there would be an
// import cycle, and loading both from here is not.
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import nodeConfig from './eslint.js'
import { asRecord } from './json-shapes.js'
import { type LayerPackage, layerEslintConfigOf, layerPackagesFor } from './layer.js'
import { composeLayered, type LayerConfig } from './layer-lint-policy.js'
import { projectDirectory } from './project-settings.js'

// A project whose package.json cannot be read declares no layer, which leaves the node layer's configuration.
const readManifest = (file: string): unknown => {
  try {
    return JSON.parse(readFileSync(file, 'utf8'))
  } catch {
    return {}
  }
}

const manifest: unknown = readManifest(path.join(projectDirectory, 'package.json'))

const declared: readonly (readonly [string, string])[] = layerPackagesFor(
  projectDirectory,
  manifest,
  readManifest,
).flatMap((layer: LayerPackage): readonly (readonly [string, string])[] => {
  const file: string | undefined = layerEslintConfigOf(layer, projectDirectory)
  return file === undefined ? [] : [[layer.name, file]]
})

const layers: readonly LayerConfig[] = await Promise.all(
  declared.map(
    async ([name, file]: readonly [string, string]): Promise<LayerConfig> => ({
      name,
      config: asRecord(await import(pathToFileURL(file).href))['default'],
    }),
  ),
)

const config: readonly unknown[] = composeLayered(nodeConfig, layers)

export default config
