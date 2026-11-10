import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import type { GateResult } from '@airness/node/gate-result'
import { describe, expect, it } from 'vitest'
import { payloadDefaults } from '@/gate-payload-defaults.js'
import { payloadGenerated } from '@/gate-payload-generated.js'
import { preflight } from '@/gate-preflight.js'
import type { PayloadMember } from '@/payload-member.js'
import { REQUIRED_PINS } from '@/payload-pins.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { detachedProjectForTest, payloadMemberAt, projectForTest } from './helpers/temporary-project.js'

const installed = (
  name: string,
  version: string,
  extra: Readonly<Record<string, unknown>> = {},
): Record<string, string> => ({
  [`node_modules/${name}/package.json`]: JSON.stringify({ name, version, ...extra }),
})

const pinnedInstall = (): Record<string, string> =>
  Object.fromEntries(
    Object.entries(REQUIRED_PINS).map(([name, version]: [string, string]): [string, string] => [
      `node_modules/${name}/package.json`,
      JSON.stringify({ name, version }),
    ]),
  )

const PAYLOAD_MANIFEST: unknown = { name: 'site', dependencies: { ...REQUIRED_PINS } }

describe('preflight', () => {
  it('passes a Payload application on the pinned versions', () => {
    const root: string = projectForTest(pinnedInstall())
    const target: PayloadMember = payloadMemberAt(root, PAYLOAD_MANIFEST)
    expect(preflight(target, [target]).summary).toBe('a Payload application on the pinned versions')
  })

  it('refuses a node project in which no member declares Payload', () => {
    const root: string = projectForTest({})
    const target: PayloadMember = payloadMemberAt(root, { name: 'library' })
    const result: GateResult = preflight(target, [target])
    expect(result.ok).toBe(false)
    expect(result.findings[0]).toContain('no package in this node project declares "payload"')
  })

  it('passes a library member beside a Payload application', () => {
    const root: string = projectForTest(pinnedInstall())
    const cms: PayloadMember = payloadMemberAt(root, PAYLOAD_MANIFEST)
    const library: PayloadMember = payloadMemberAt(root, { name: 'library' }, 'packages/library')
    expect(preflight(library, [cms, library]).summary).toBe('no Payload in this member')
  })

  it('names a first-party Payload package at another version, by member', () => {
    const root: string = projectForTest({ ...pinnedInstall(), ...installed('@payloadcms/db-postgres', '3.0.0') })
    const target: PayloadMember = payloadMemberAt(root, {
      dependencies: { ...REQUIRED_PINS, '@payloadcms/db-postgres': '3.0.0' },
    })
    expect(preflight(target, [target]).findings).toStrictEqual([
      `.: @payloadcms/db-postgres resolves to 3.0.0, but the Payload layer runs ${String(REQUIRED_PINS['payload'])}; ` +
        `declare exactly ${String(REQUIRED_PINS['payload'])}`,
    ])
  })
})

const IMPORT_MAP: string = 'export const importMap = {}\n'

const SCHEMA_FILE: string = 'src/payload-generated-schema.ts'

// A stand-in for the Payload CLI: it writes the generated files the way `payload generate:*` does.
const fakeCli = (types: string, schema: string = 'export const posts = {}\n'): Record<string, string> => ({
  ...installed('payload', String(REQUIRED_PINS['payload']), { bin: { payload: 'bin.js' } }),
  'node_modules/payload/bin.js': [
    "import { mkdirSync, writeFileSync } from 'node:fs'",
    `if (process.argv[2] === 'generate:types') writeFileSync('src/payload-types.ts', ${JSON.stringify(types)})`,
    `if (process.argv[2] === 'generate:db-schema') writeFileSync('${SCHEMA_FILE}', ${JSON.stringify(schema)})`,
    "if (process.argv[2] === 'generate:importmap') { mkdirSync('src/app/(payload)/admin', { recursive: true }); " +
      "writeFileSync('src/app/(payload)/admin/importMap.js', " +
      JSON.stringify(IMPORT_MAP) +
      ') }',
    "if (process.argv[2] === 'generate:types' && process.env['PAYLOAD_SECRET'] === undefined) process.exit(3)",
  ].join('\n'),
  'node_modules/payload/package.json': JSON.stringify({ name: 'payload', type: 'module', bin: { payload: 'bin.js' } }),
})

describe('payload-generated', PROCESS_TIME, () => {
  it('passes when regenerating leaves the committed artefacts unchanged', () => {
    const root: string = detachedProjectForTest({
      ...fakeCli('export type Post = {}\n'),
      'src/payload-types.ts': 'export type Post = {}\n',
      'src/app/(payload)/admin/importMap.js': IMPORT_MAP,
    })
    const result: GateResult = payloadGenerated(payloadMemberAt(root, PAYLOAD_MANIFEST))
    expect(result.summary).toBe('the generated Payload artefacts match the configuration')
  })

  it('names an artefact that drifted from the configuration', () => {
    const root: string = projectForTest({
      ...fakeCli('export type Post = { title: string }\n'),
      'src/payload-types.ts': 'export type Post = {}\n',
      'src/app/(payload)/admin/importMap.js': IMPORT_MAP,
    })
    const result: GateResult = payloadGenerated(payloadMemberAt(root, PAYLOAD_MANIFEST))
    expect(result.ok).toBe(false)
    expect(result.findings.join('\n')).toContain('src/payload-types.ts')
    expect(readFileSync(path.join(root, 'src/payload-types.ts'), 'utf8')).toContain('title')
  })

  // The Drizzle schema is generated too, so a schema the configuration no longer produces is drift.
  it('names a kept database schema that drifted from the configuration', () => {
    const root: string = detachedProjectForTest({
      ...fakeCli('export type Post = {}\n', 'export const posts = { title: text() }\n'),
      'src/payload-types.ts': 'export type Post = {}\n',
      'src/app/(payload)/admin/importMap.js': IMPORT_MAP,
      [SCHEMA_FILE]: 'export const posts = {}\n',
    })
    const result: GateResult = payloadGenerated(payloadMemberAt(root, PAYLOAD_MANIFEST))
    expect(result.findings).toContain(`${SCHEMA_FILE} changed when regenerated`)
  })

  it('passes a kept database schema that regenerates unchanged', () => {
    const root: string = detachedProjectForTest({
      ...fakeCli('export type Post = {}\n'),
      'src/payload-types.ts': 'export type Post = {}\n',
      'src/app/(payload)/admin/importMap.js': IMPORT_MAP,
      [SCHEMA_FILE]: 'export const posts = {}\n',
    })
    expect(payloadGenerated(payloadMemberAt(root, PAYLOAD_MANIFEST)).ok).toBe(true)
  })

  // A project that never generated the schema is not handed one: it is an artefact a SQL project opts into.
  it('leaves a member that keeps no database schema without one', () => {
    const root: string = detachedProjectForTest({
      ...fakeCli('export type Post = {}\n'),
      'src/payload-types.ts': 'export type Post = {}\n',
      'src/app/(payload)/admin/importMap.js': IMPORT_MAP,
    })
    expect(payloadGenerated(payloadMemberAt(root, PAYLOAD_MANIFEST)).ok).toBe(true)
    expect(existsSync(path.join(root, SCHEMA_FILE))).toBe(false)
  })

  it('fails when the Payload CLI is not installed', () => {
    const root: string = projectForTest({})
    expect(payloadGenerated(payloadMemberAt(root, PAYLOAD_MANIFEST)).summary).toBe(
      'the Payload CLI could not be resolved from the member',
    )
  })

  it('fails with the CLI output when a generator fails', () => {
    const root: string = projectForTest({
      ...installed('payload', '3.91.0', { bin: 'bin.js' }),
      'node_modules/payload/bin.js': "console.error('the configuration did not load'); process.exit(1)",
    })
    const result: GateResult = payloadGenerated(payloadMemberAt(root, PAYLOAD_MANIFEST))
    expect(result.summary).toBe('the Payload CLI could not regenerate the artefacts')
    expect(result.findings).toContain('the configuration did not load')
  })

  it('does not apply to a member that is no Payload application', () => {
    const root: string = projectForTest({})
    expect(payloadGenerated(payloadMemberAt(root, { name: 'library' })).isApplicable).toBe(false)
  })
})

describe('payload-defaults', PROCESS_TIME, () => {
  it('fails when the member names no configuration that exists', () => {
    const root: string = projectForTest({ 'tsconfig.json': '{}' })
    expect(payloadDefaults(payloadMemberAt(root, PAYLOAD_MANIFEST)).summary).toBe(
      'the Payload configuration could not be found',
    )
  })

  it("fails when Payload's default access cannot be located", () => {
    const root: string = projectForTest({ 'tsconfig.json': '{}', 'src/payload.config.ts': 'export default {}' })
    expect(payloadDefaults(payloadMemberAt(root, PAYLOAD_MANIFEST)).summary).toBe(
      "Payload's default access could not be located",
    )
  })

  it('does not apply to a member that is no Payload application', () => {
    const root: string = projectForTest({})
    expect(payloadDefaults(payloadMemberAt(root, { name: 'library' })).isApplicable).toBe(false)
  })
})

// A member whose installed Payload carries a stand-in for its default access, so the gate starts the real
// probe against the configuration module a spec writes.
const DEFAULT_ACCESS: string = 'export const defaultAccess = ({ req }) => Boolean(req.user)\n'

const memberProbing = (config: string): PayloadMember =>
  payloadMemberAt(
    projectForTest({
      'tsconfig.json': '{}',
      'node_modules/payload/package.json': JSON.stringify({ name: 'payload', type: 'module' }),
      'node_modules/payload/dist/auth/defaultAccess.js': DEFAULT_ACCESS,
      'src/payload.config.ts': config,
    }),
    PAYLOAD_MANIFEST,
  )

describe('payload-defaults against a started probe', PROCESS_TIME, () => {
  it('fails a probe that could not build the configuration, with its output', () => {
    const result: GateResult = payloadDefaults(memberProbing("throw new Error('no secret')\n"))
    expect(result.summary).toBe('the Payload configuration could not be built')
    expect(result.findings.join('\n')).toContain('Error: no secret')
  })

  it('fails a probe that printed no readable evidence', () => {
    expect(payloadDefaults(memberProbing('process.exit(0)\n')).summary).toBe(
      'the configuration probe printed incomplete or malformed evidence',
    )
  })

  it('passes a configuration that decides every access', () => {
    const result: GateResult = payloadDefaults(memberProbing('export default { collections: [], globals: [] }\n'))
    expect(result.findings).toStrictEqual([])
    expect(result.ok).toBe(true)
  })

  it('names an entity that inherits the default access', () => {
    const result: GateResult = payloadDefaults(
      memberProbing("export default { collections: [{ slug: 'posts' }], globals: [] }\n"),
    )
    expect(result.ok).toBe(false)
    expect(result.summary).toContain("inherit Payload's default access")
    expect(result.findings.join('\n')).toContain('collection "posts" leaves create, read, update, delete')
  })

  it('names a security defect of the resolved configuration before its access findings', () => {
    const result: GateResult = payloadDefaults(
      memberProbing("export default { collections: [], globals: [], admin: { autoLogin: { email: 'a@b.c' } } }\n"),
    )
    expect(result.ok).toBe(false)
    expect(result.summary).toBe('the resolved Payload security configuration has defects')
  })
})
