import { cp, mkdir, rm } from 'node:fs/promises'
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { build } from 'esbuild'

const PROJECT: string = fileURLToPath(new URL('../', import.meta.url))

const fromProject: NodeJS.Require = createRequire(path.join(PROJECT, 'package.json'))

// The packages whose code the stylesheet or the script carries, each shipping its license beside it. The
// stylesheet itself is written by the Tailwind command line the build script runs after this, which names its
// input there, where the dependency analysis reads it.
const SHIPPED: readonly string[] = ['daisyui', 'htmx.org', 'tailwindcss']

const releaseOf = (name: string): string => path.dirname(fromProject.resolve(`${name}/package.json`))

const licenseFile = (destination: string, name: string): string =>
  path.join(destination, 'licenses', `${name.replace('@', '').replace('/', '-')}.txt`)

const script = async (destination: string): Promise<void> => {
  await build({
    absWorkingDir: PROJECT,
    entryPoints: { 'js/app': 'src/main/frontend/entries/app.ts' },
    outdir: destination,
    bundle: true,
    format: 'esm',
    platform: 'browser',
    target: 'es2024',
    minify: true,
    legalComments: 'linked',
  })
}

/**
 * Builds the page script, and copies the licenses of what it and the stylesheet carry, into a directory
 * Maven serves.
 * @param destination the directory to replace with the build, which is never a tracked one
 */
export const buildAssets = async (destination: string): Promise<void> => {
  await rm(destination, { recursive: true, force: true })
  await script(destination)
  await mkdir(path.join(destination, 'licenses'), { recursive: true })
  await Promise.all(
    SHIPPED.map(
      async (name: string): Promise<void> => cp(path.join(releaseOf(name), 'LICENSE'), licenseFile(destination, name)),
    ),
  )
}
