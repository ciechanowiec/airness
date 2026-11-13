import { mkdtemp, readdir, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { buildAssets } from '@scripts/assets'
import { describe, expect, it } from 'vitest'

describe('the frontend build', (): void => {
  it('writes the script and the licenses, replacing stale output', async (): Promise<void> => {
    const destination: string = await mkdtemp(path.join(tmpdir(), 'starter-assets-'))
    try {
      await writeFile(path.join(destination, 'obsolete.js'), 'obsolete')
      await buildAssets(destination)
      expect(await readdir(destination)).not.toContain('obsolete.js')
      expect(await readdir(path.join(destination, 'js'))).toContain('app.js')
      const licenses: readonly string[] = await readdir(path.join(destination, 'licenses'))
      expect(licenses.toSorted((first: string, second: string): number => first.localeCompare(second))).toEqual([
        'daisyui.txt',
        'fontsource-geist-mono.txt',
        'fontsource-variable-schibsted-grotesk.txt',
        'htmx.org.txt',
        'lucide-static.txt',
        'tailwindcss.txt',
      ])
      expect(await readdir(path.join(destination, 'css', 'files'))).toContain('geist-mono-latin-400-normal.woff2')
      expect(await readFile(path.join(destination, 'icons', 'sprite.svg'), 'utf8')).toContain('<symbol id="house"')
    } finally {
      await rm(destination, { recursive: true, force: true })
    }
  })

  it('writes the directory Maven serves through the declared build command', async (): Promise<void> => {
    await import('@scripts/build')
    expect(await readdir(path.resolve('target/frontend/js'))).toContain('app.js')
  })
})
