import { mkdtemp, readdir, rm, writeFile } from 'node:fs/promises'
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
      expect(await readdir(path.join(destination, 'licenses'))).toEqual([
        'daisyui.txt',
        'htmx.org.txt',
        'tailwindcss.txt',
      ])
    } finally {
      await rm(destination, { recursive: true, force: true })
    }
  })

  it('writes the directory Maven serves through the declared build command', async (): Promise<void> => {
    await import('@scripts/build')
    expect(await readdir(path.resolve('target/frontend/js'))).toContain('app.js')
  })
})
