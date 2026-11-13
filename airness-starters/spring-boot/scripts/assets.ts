import { cp, mkdir, readdir, readFile, rm, writeFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { build } from 'esbuild'

const PROJECT: string = fileURLToPath(new URL('../', import.meta.url))

const TEMPLATES: string = path.join(PROJECT, 'src/main/resources/templates')

const fromProject: NodeJS.Require = createRequire(path.join(PROJECT, 'package.json'))

// The packages whose code the stylesheet, the script, the fonts or the icons carry, each shipping its license
// beside it. The stylesheet itself is written by the Tailwind command line the build script runs after this,
// which names its input there, where the dependency analysis reads it.
const SHIPPED: readonly string[] = [
  'daisyui',
  'htmx.org',
  'tailwindcss',
  'lucide-static',
  '@fontsource-variable/schibsted-grotesk',
  '@fontsource/geist-mono',
]

// The font files the stylesheet's faces point at, kept beside it under the folder their rules name. Geist Mono
// is drawn in two weights only, so the files of the others stay out of the build.
const FONTS: readonly { readonly name: string; readonly keep: RegExp }[] = [
  { name: '@fontsource-variable/schibsted-grotesk', keep: /\.woff2$/u },
  { name: '@fontsource/geist-mono', keep: /-(?:400|500)-normal\.woff2$/u },
]

// A template names an icon through the icon fragment, so the icons a build carries are the ones the templates
// name, and an icon nobody draws is never shipped.
const ICON_CALL: RegExp = /icon\('([a-z0-9-]+)'/gu

// A symbol drawn from another file inherits from the element that uses it rather than from the file it sits
// in, so every symbol carries the stroke itself, a touch lighter than Lucide's own for text-sized icons.
const STROKE: string =
  'fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"'

const releaseOf = (name: string): string => path.dirname(fromProject.resolve(`${name}/package.json`))

// The icon set is named outright, so the dependency analysis sees that the build reads it.
const LUCIDE_MANIFEST: string = fileURLToPath(import.meta.resolve('lucide-static/package.json'))

const LUCIDE: string = path.join(path.dirname(LUCIDE_MANIFEST), 'icons')

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

const fonts = async (destination: string): Promise<void> => {
  const folder: string = path.join(destination, 'css', 'files')
  await mkdir(folder, { recursive: true })
  await Promise.all(
    FONTS.map(async (font: { readonly name: string; readonly keep: RegExp }): Promise<void> => {
      const files: string = path.join(releaseOf(font.name), 'files')
      const shipped: readonly string[] = await readdir(files)
      const kept: readonly string[] = shipped.filter((file: string): boolean => font.keep.test(file))
      await Promise.all(
        kept.map(async (file: string): Promise<void> => cp(path.join(files, file), path.join(folder, file))),
      )
    }),
  )
}

const namedIcons = async (): Promise<readonly string[]> => {
  const files: readonly string[] = await readdir(TEMPLATES, { recursive: true })
  const texts: readonly string[] = await Promise.all(
    files
      .filter((file: string): boolean => file.endsWith('.html'))
      .map(async (file: string): Promise<string> => readFile(path.join(TEMPLATES, file), 'utf8')),
  )
  const names: readonly string[] = texts.flatMap((text: string): readonly string[] =>
    [...text.matchAll(ICON_CALL)].map((call: RegExpExecArray): string => String(call[1])),
  )
  return [...new Set(names)].toSorted((first: string, second: string): number => first.localeCompare(second))
}

const symbol = async (name: string): Promise<string> => {
  const svg: string = await readFile(path.join(LUCIDE, `${name}.svg`), 'utf8')
  // Everything between the opening tag of the icon's svg element and its closing tag.
  const body: string = svg.slice(svg.indexOf('>', svg.indexOf('<svg')) + 1, svg.lastIndexOf('</svg>')).trim()
  return `<symbol id="${name}" viewBox="0 0 24 24" ${STROKE}>${body}</symbol>`
}

// One file holds every icon as a symbol, so a page fetches it once and the browser keeps it as long as the
// stylesheet.
const icons = async (destination: string): Promise<void> => {
  const names: readonly string[] = await namedIcons()
  const symbols: readonly string[] = await Promise.all(names.map(async (name: string): Promise<string> => symbol(name)))
  const sprite: string = `<svg xmlns="http://www.w3.org/2000/svg">${symbols.join('')}</svg>\n`
  await mkdir(path.join(destination, 'icons'), { recursive: true })
  await writeFile(path.join(destination, 'icons', 'sprite.svg'), sprite)
}

/**
 * Builds the page script, the fonts and the icon sprite, and copies the licenses of what they and the
 * stylesheet carry, into a directory Maven serves.
 * @param destination the directory to replace with the build, which is never a tracked one
 */
export const buildAssets = async (destination: string): Promise<void> => {
  await rm(destination, { recursive: true, force: true })
  await script(destination)
  await Promise.all([fonts(destination), icons(destination)])
  await mkdir(path.join(destination, 'licenses'), { recursive: true })
  await Promise.all(
    SHIPPED.map(
      async (name: string): Promise<void> => cp(path.join(releaseOf(name), 'LICENSE'), licenseFile(destination, name)),
    ),
  )
}
