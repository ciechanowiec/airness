import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

interface Manifest {
  readonly dependencies: Readonly<Record<string, string>>
}

const manifest: Manifest = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8')) as Manifest

describe('the analyzers the package pins', () => {
  // type-coverage asks for its core by a caret range, so a fresh install would otherwise resolve the newest core
  // past the release-age floor rather than the one this package was built and approved with, and the security
  // replacement approved for that core's edge would then reach no resolved parent.
  it('pins the type-coverage core at the version of the command that runs it', () => {
    expect(manifest.dependencies['type-coverage-core']).toBe(manifest.dependencies['type-coverage'])
  })
})
