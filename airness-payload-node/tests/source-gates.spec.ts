import path from 'node:path'
import { run } from '@airness/node/exec'
import type { GateResult } from '@airness/node/gate-result'
import { describe, expect, it } from 'vitest'
import { adminViews } from '@/gate-admin-views.js'
import { payloadBlocklist } from '@/gate-payload-blocklist.js'
import { payloadEnvironment } from '@/gate-payload-environment.js'
import { payloadSourceRules } from '@/gate-payload-source-rules.js'
import { PROCESS_TIME } from './helpers/process-time.js'
import { detachedProjectForTest, payloadMemberAt, projectForTest } from './helpers/temporary-project.js'

const PAYLOAD_MANIFEST: unknown = { name: 'site', dependencies: { payload: '3.91.0' } }
const LIBRARY_MANIFEST: unknown = { name: 'library' }

const ACCESS: string = 'access: { create: admins, read: admins, update: admins, delete: admins }'

describe('payload-source-rules', PROCESS_TIME, () => {
  it('passes a configuration that decides its access and bounds its reads', () => {
    const root: string = projectForTest({
      'src/collections/Posts.ts': `export const Posts: CollectionConfig = { slug: 'posts', ${ACCESS}, fields: [] }`,
      'src/lib/reads.ts': "export const read = () => payload.find({ collection: 'posts', depth: 0, limit: 10 })",
      'src/payload-types.ts': "export const ignored = payload.find({ collection: 'posts' })",
    })
    const result: GateResult = payloadSourceRules(payloadMemberAt(root, PAYLOAD_MANIFEST))
    expect(result.ok).toBe(true)
    expect(result.summary).toBe('2 source file(s) follow the Payload source rules')
  })

  it('names each violation with its file, line and rule', () => {
    const root: string = projectForTest({
      'src/collections/Posts.ts': "export const Posts: CollectionConfig = { slug: 'posts', fields: [] }",
      'src/lib/reads.ts': "\nexport const read = () => payload.find({ collection: 'posts' })",
    })
    const result: GateResult = payloadSourceRules(payloadMemberAt(root, PAYLOAD_MANIFEST))
    expect(result.ok).toBe(false)
    expect(
      result.findings.some((finding: string): boolean =>
        finding.startsWith('src/collections/Posts.ts:1 [require-complete-access]'),
      ),
    ).toBe(true)
    expect(
      result.findings.some((finding: string): boolean => finding.startsWith('src/lib/reads.ts:2 [no-unbounded-find]')),
    ).toBe(true)
  })

  // The handler's own file holds no Local API call; the helper it calls holds the privileged default.
  it('names the route handler whose helper leaves the access decision to the default', () => {
    const root: string = projectForTest({
      'src/endpoints/posts.ts': "import { read } from '@/lib/reads'\nexport const handler = () => read()",
      'src/lib/reads.ts': "export const read = () => payload.find({ collection: 'posts', depth: 0, limit: 10 })",
    })
    const result: GateResult = payloadSourceRules(payloadMemberAt(root, PAYLOAD_MANIFEST))
    expect(result.findings.join('\n')).toContain('src/endpoints/posts.ts:2 [require-endpoint-access]')
  })

  it('requires a recorded migration when the configuration pushes no schema', () => {
    const root: string = projectForTest({
      'src/payload.config.ts': 'export default buildConfig({ db: postgresAdapter({ push: false }) })',
    })
    const result: GateResult = payloadSourceRules(payloadMemberAt(root, PAYLOAD_MANIFEST))
    expect(result.findings.join('\n')).toContain('require-recorded-migrations')
  })

  it('reads a recorded migration from the directory the configuration names', () => {
    const root: string = projectForTest({
      'src/payload.config.ts': 'export default buildConfig({ db: postgresAdapter({ push: false }) })',
      'src/migrations/20260101_000000_initial.ts': 'export const up = 1',
      'src/migrations/index.ts': 'export const migrations = []',
    })
    expect(payloadSourceRules(payloadMemberAt(root, PAYLOAD_MANIFEST)).ok).toBe(true)
  })

  it('does not apply to a member that is no Payload application', () => {
    const root: string = projectForTest({ 'src/index.ts': 'export const one = 1' })
    expect(payloadSourceRules(payloadMemberAt(root, LIBRARY_MANIFEST)).isApplicable).toBe(false)
  })
})

describe('admin-views', PROCESS_TIME, () => {
  const ViewConfig: string = [
    'export default buildConfig({',
    "  admin: { components: { views: { reports: { Component: '/views/Reports', path: '/reports' } } } },",
    '})',
  ].join('\n')

  it('passes a project that declares no custom admin view', () => {
    const root: string = projectForTest({ 'src/index.ts': 'export const one = 1' })
    expect(adminViews(payloadMemberAt(root, PAYLOAD_MANIFEST)).summary).toBe(
      'this project declares no custom admin view',
    )
  })

  it('names a custom admin view no specification scans', () => {
    const root: string = projectForTest({ 'src/payload.config.ts': ViewConfig })
    const result: GateResult = adminViews(payloadMemberAt(root, PAYLOAD_MANIFEST))
    expect(result.ok).toBe(false)
    expect(result.findings[0]).toMatch(/^src\/payload\.config\.ts:2 \[/u)
  })

  it('passes a custom admin view a specification scans with axe', () => {
    const root: string = projectForTest({
      'src/payload.config.ts': ViewConfig,
      'tests/e2e/reports.e2e.spec.ts': [
        "import AxeBuilder from '@axe-core/playwright'",
        "test('reports', async ({ page }) => { await page.goto('/admin/reports'); await new AxeBuilder({ page }).analyze() })",
      ].join('\n'),
    })
    expect(adminViews(payloadMemberAt(root, PAYLOAD_MANIFEST)).summary).toBe(
      '1 custom admin view(s) are scanned for accessibility',
    )
  })

  it('does not apply to a member that is no Payload application', () => {
    const root: string = projectForTest({ 'src/index.ts': 'export const one = 1' })
    expect(adminViews(payloadMemberAt(root, LIBRARY_MANIFEST)).isApplicable).toBe(false)
  })
})

describe('payload-blocklist', () => {
  it('passes a lockfile that resolves no refused package', () => {
    const root: string = projectForTest({
      'pnpm-lock.yaml': "lockfileVersion: '9.0'\npackages:\n  payload@3.91.0:\n    resolution: {}\n",
    })
    expect(payloadBlocklist(payloadMemberAt(root, PAYLOAD_MANIFEST)).ok).toBe(true)
  })

  it('names a refused package the lockfile resolves', () => {
    const root: string = projectForTest({
      'pnpm-lock.yaml': "lockfileVersion: '9.0'\npackages:\n  oracledb@6.0.0:\n    resolution: {}\n",
    })
    const result: GateResult = payloadBlocklist(payloadMemberAt(root, PAYLOAD_MANIFEST))
    expect(result.ok).toBe(false)
    expect(result.findings[0]).toContain('oracledb is refused by the Payload layer')
  })

  it('fails a node project without a lockfile', () => {
    const root: string = projectForTest({ 'package.json': '{}' })
    expect(payloadBlocklist(payloadMemberAt(root, PAYLOAD_MANIFEST)).findings).toStrictEqual([
      'pnpm-lock.yaml is missing; commit the lockfile pnpm install writes',
    ])
  })

  it("judges a workspace member through the node project's lockfile", () => {
    const root: string = projectForTest({ 'package.json': '{}' })
    expect(payloadBlocklist(payloadMemberAt(root, PAYLOAD_MANIFEST, 'apps/cms')).ok).toBe(true)
  })
})

describe('payload-environment', PROCESS_TIME, () => {
  it('passes a member without a validated environment module', () => {
    const root: string = projectForTest({ 'src/index.ts': 'export const one = 1' })
    expect(payloadEnvironment(payloadMemberAt(root, PAYLOAD_MANIFEST)).ok).toBe(true)
  })

  it('names a variable the module reads and the repository documents nowhere', () => {
    const root: string = projectForTest({ 'src/lib/environment.ts': "export const url = process.env['DATABASE_URL']" })
    const result: GateResult = payloadEnvironment(payloadMemberAt(root, PAYLOAD_MANIFEST))
    expect(result.ok).toBe(false)
    expect(result.findings[0]).toContain(
      'DATABASE_URL: read by src/lib/environment.ts but documented in no example file',
    )
  })

  it("counts a variable the member's own example file documents, below a repository root that has none", () => {
    const root: string = projectForTest({
      'src/lib/environment.ts': "export const url = process.env['DATABASE_URL']",
      '.env.example': '# The database the application connects to.\nDATABASE_URL=\n',
    })
    const result: GateResult = payloadEnvironment(payloadMemberAt(root, PAYLOAD_MANIFEST))
    expect(result.findings).toEqual([])
  })

  it("counts a variable the repository root's example file documents for a member nested in it", () => {
    const repository: string = detachedProjectForTest({
      'site/src/lib/environment.ts': "export const url = process.env['DATABASE_URL']",
      '.env.example': '# The database the application connects to.\nDATABASE_URL=\n',
    })
    expect(run('git', ['init', '--quiet'], { cwd: repository }).code).toBe(0)
    const result: GateResult = payloadEnvironment(
      payloadMemberAt(path.join(repository, 'site'), PAYLOAD_MANIFEST, 'site'),
    )
    expect(result.findings).toEqual([])
  })

  it('names an inlined variable the member Dockerfile does not declare', () => {
    const root: string = projectForTest({
      'src/lib/environment.ts': 'export const site = process.env.NEXT_PUBLIC_SITE_URL',
      Dockerfile: 'FROM node\nRUN pnpm build\n',
    })
    const result: GateResult = payloadEnvironment(payloadMemberAt(root, PAYLOAD_MANIFEST))
    expect(result.findings).toHaveLength(1)
    expect(result.findings[0]).toContain('declared by no ARG in Dockerfile')
  })

  it('does not apply to a member that is no Payload application', () => {
    const root: string = projectForTest({ 'src/index.ts': 'export const one = 1' })
    expect(payloadEnvironment(payloadMemberAt(root, LIBRARY_MANIFEST)).isApplicable).toBe(false)
  })
})
