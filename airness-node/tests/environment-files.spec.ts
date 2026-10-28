import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { localEnvironment, portOf, RUN_ENVIRONMENT_FILES, runEnvironmentFiles } from '@/environment-files.js'

// A real (not mocked) isExistingFile backed by a set of known paths - the pure core takes it as a value,
// which is exactly why no test double is needed.
const existenceCheckOver =
  (paths: readonly string[]) =>
  (candidate: string): boolean =>
    paths.includes(candidate)

const filesPresent = (paths: readonly string[]): readonly string[] => runEnvironmentFiles(existenceCheckOver(paths))

describe('runEnvironmentFiles', () => {
  it('loads actual local files while preserving process values and local override precedence', () => {
    const directory: string = mkdtempSync(path.join(tmpdir(), 'airness-local-environment-'))
    try {
      expect(localEnvironment(directory, {})).toEqual({})
      writeFileSync(path.join(directory, '.env'), 'PROJECT_MODE=base\nPROJECT_PORT=3000\nPROJECT_LABEL="a value"\n')
      writeFileSync(path.join(directory, '.env.local'), 'PROJECT_MODE=local\nPROJECT_PORT=4000\n')
      expect(localEnvironment(directory, { PROJECT_PORT: '5000' })).toEqual({
        PROJECT_MODE: 'local',
        PROJECT_LABEL: 'a value',
      })
    } finally {
      rmSync(directory, { recursive: true, force: true })
    }
  })
  it('reads nothing when the project keeps no environment file', () => {
    expect(filesPresent([])).toEqual([])
  })

  it('skips a file the project does not have rather than asking a run to read it', () => {
    expect(filesPresent(['.env'])).toEqual(['.env'])
  })

  // The joint that matters. `process.loadEnvFile` never replaces a value already set, so the order the
  // reads happen in IS the precedence: whichever file is named first wins the variables both declare.
  it('names the untracked override before the tracked file, so the override wins', () => {
    expect(filesPresent(['.env', '.env.local'])).toEqual(['.env.local', '.env'])
  })

  it('offers no file the project did not put there', () => {
    expect(filesPresent(['.env.production', '.env.example'])).toEqual([])
  })

  it('reads every declared file when the project keeps all of them', () => {
    expect(filesPresent(RUN_ENVIRONMENT_FILES)).toEqual(RUN_ENVIRONMENT_FILES)
  })
})

describe('portOf', () => {
  it('reads the port a project declared', () => {
    // The setting exists to describe a non-default port, and without this the server was started on the
    // framework's default while the runner waited on the declared one.
    expect(portOf('http://localhost:3100')).toBe('3100')
  })

  it('names no port when the origin carries none', () => {
    expect(portOf('https://example.test')).toBeUndefined()
  })

  it('names no port for an origin it cannot parse', () => {
    expect(portOf('not a url')).toBeUndefined()
  })

  it('reads a port from an origin carrying a path', () => {
    expect(portOf('http://localhost:3000/admin')).toBe('3000')
  })
})
