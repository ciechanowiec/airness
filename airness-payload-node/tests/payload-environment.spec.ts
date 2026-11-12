import { describe, expect, it } from 'vitest'
import { findEnvironmentViolations } from '@/payload-environment.js'

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
        example: 'NEXT_PUBLIC_SERVER_URL=\n',
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

const undocumentedIn = (module: string): readonly string[] =>
  findEnvironmentViolations({ module, example: 'OTHER=\n', dockerfiles: [], serverUrlVariables: [] }).map(
    (finding: string): string => finding.split(':', 1)[0] ?? '',
  )

// Every spelling that reads a variable by its name owes the example file that name, whatever holds the
// environment: the recommended parameter, an alias of it, or process.env itself.
describe('findEnvironmentViolations over the spellings of a read', () => {
  it.each([
    "export const parse = (source: NodeJS.ProcessEnv) => source['DATABASE_URL']",
    'export const parse = (source: NodeJS.ProcessEnv) => source["DATABASE_URL"]',
    'export const parse = (source: NodeJS.ProcessEnv) => source[`DATABASE_URL`]',
    "export const parse = (source?: NodeJS.ProcessEnv) => source?.['DATABASE_URL']",
    "const url = process.env[ 'DATABASE_URL' ]",
    'const { DATABASE_URL } = process.env',
    'const { DATABASE_URL: url } = process.env',
    "const { DATABASE_URL = 'postgres://localhost' } = process.env",
    "const { 'DATABASE_URL': url } = process.env",
    'const { DATABASE_URL, ...rest } = process.env',
    'let url\n({ DATABASE_URL: url } = process.env)',
    'export const parse = ({ DATABASE_URL }: NodeJS.ProcessEnv) => DATABASE_URL',
    'export const parse = ({ DATABASE_URL }: ProcessEnv) => DATABASE_URL',
    'export const parse = (source: NodeJS.ProcessEnv) => {\n  const { DATABASE_URL } = source\n}',
    'export const parse = (source = process.env) => {\n  const { DATABASE_URL } = source\n}',
    'export const parse = (source: Readonly<Record<string, string>> = process.env) => {\n' +
      '  const { DATABASE_URL } = source\n}',
    'const environment = process.env\nconst { DATABASE_URL } = environment',
  ])('names the variable read as %s', (module: string) => {
    expect(undocumentedIn(module)).toStrictEqual(['DATABASE_URL'])
  })

  it.each([
    "const names = (): readonly string[] => ['DATABASE_URL']",
    "for (const name of ['DATABASE_URL']) { check(name) }",
    'const { DATABASE_URL } = settings',
    'const { DATABASE_URL } = loadSettings()',
    "// process.env['DATABASE_URL'] is read below by the schema\nconst parsed = schema.parse(process.env)",
  ])('names nothing for %s, which reads no variable by its name', (module: string) => {
    expect(undocumentedIn(module)).toStrictEqual([])
  })
})
