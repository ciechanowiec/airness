import { describe, expect, it } from 'vitest'
import { findEnvironmentViolations, isDockerfile } from '@/payload-environment.js'

const MODULE: string = [
  "const secret = process.env['PAYLOAD_SECRET']",
  "const url = process.env['DATABASE_URL']",
  'const mode = process.env.NODE_ENV',
  'const site = process.env.NEXT_PUBLIC_SITE_URL',
  "const key = process.env['NEXT_PUBLIC_MAP_KEY']",
].join('\n')

const EXAMPLE: string = [
  '# the signing secret',
  'PAYLOAD_SECRET=',
  'export DATABASE_URL=',
  'NEXT_PUBLIC_MAP_KEY=',
].join('\n')

describe('findEnvironmentViolations', () => {
  it('passes a module whose reads are documented and that ships no image', () => {
    expect(
      findEnvironmentViolations({ module: MODULE, example: EXAMPLE, dockerfiles: [], serverUrlVariables: [] }),
    ).toStrictEqual([])
  })

  it('names a bracketed read the example file does not document, and no dotted ambient read', () => {
    const findings: readonly string[] = findEnvironmentViolations({
      module: MODULE,
      example: 'PAYLOAD_SECRET=\n',
      dockerfiles: [],
      serverUrlVariables: [],
    })
    expect(findings.map((finding: string): string => finding.split(':', 1)[0] ?? '')).toStrictEqual([
      'DATABASE_URL',
      'NEXT_PUBLIC_MAP_KEY',
    ])
  })

  it('names every read when the repository documents no environment', () => {
    expect(
      findEnvironmentViolations({ module: MODULE, example: undefined, dockerfiles: [], serverUrlVariables: [] }),
    ).toHaveLength(3)
  })

  it('names an inlined variable no build argument of the image declares', () => {
    const findings: readonly string[] = findEnvironmentViolations({
      module: MODULE,
      example: EXAMPLE,
      dockerfiles: [{ file: 'Dockerfile', content: 'FROM node\nARG NEXT_PUBLIC_MAP_KEY=x \\\n  OTHER\nRUN build' }],
      serverUrlVariables: [],
    })
    expect(findings).toHaveLength(1)
    expect(findings[0]).toContain('NEXT_PUBLIC_SITE_URL: inlined into the image at build time')
    expect(findings[0]).toContain('add "ARG NEXT_PUBLIC_SITE_URL"')
  })

  it('reads a continued build-argument line as one declaration', () => {
    const findings: readonly string[] = findEnvironmentViolations({
      module: MODULE,
      example: EXAMPLE,
      dockerfiles: [{ file: 'Dockerfile', content: 'ARG NEXT_PUBLIC_MAP_KEY \\\n  NEXT_PUBLIC_SITE_URL\n' }],
      serverUrlVariables: [],
    })
    expect(findings).toStrictEqual([])
  })

  it('names an origin variable the bundler would replace with the value it was built with', () => {
    const findings: readonly string[] = findEnvironmentViolations({
      module: MODULE,
      example: `${EXAMPLE}\nNEXT_PUBLIC_SITE_URL=\n`,
      dockerfiles: [],
      serverUrlVariables: ['NEXT_PUBLIC_SITE_URL', 'NEXT_PUBLIC_MAP_KEY', 'PUBLIC_ORIGIN'],
    })
    expect(findings.map((finding: string): string => finding.split(':', 1)[0] ?? '')).toStrictEqual([
      'NEXT_PUBLIC_MAP_KEY',
      'NEXT_PUBLIC_SITE_URL',
    ])
    expect(findings[0]).toContain('read it from a parameter holding process.env')
  })

  it('passes an origin variable read through a parameter the bundler cannot replace', () => {
    const module: string = "export const parse = (source: NodeJS.ProcessEnv) => source['NEXT_PUBLIC_SERVER_URL']"
    expect(
      findEnvironmentViolations({
        module,
        example: undefined,
        dockerfiles: [],
        serverUrlVariables: ['NEXT_PUBLIC_SERVER_URL'],
      }),
    ).toStrictEqual([])
  })

  it('judges nothing in a member without a validated module', () => {
    expect(
      findEnvironmentViolations({ module: undefined, example: undefined, dockerfiles: [], serverUrlVariables: [] }),
    ).toStrictEqual([])
  })
})

describe('isDockerfile', () => {
  it('recognises the three spellings of a Dockerfile', () => {
    expect(['Dockerfile', 'docker/Dockerfile.web', 'web.Dockerfile'].every(isDockerfile)).toBe(true)
    expect(isDockerfile('docker/compose.yaml')).toBe(false)
  })
})
