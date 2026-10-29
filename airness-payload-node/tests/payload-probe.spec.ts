import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { type InheritedAccessReport, parseInheritedAccessReport } from '@/payload-defaults.js'
import { inheritedAccessProbe } from '@/payload-probe.js'
import { parsePayloadSecurityReport } from '@/payload-security.js'
import { projectForTest } from './helpers/temporary-project.js'

// A stand-in for Payload's default access and for a built configuration, as plain modules.
const DEFAULT_ACCESS: string = 'export const defaultAccess = ({ req }) => Boolean(req.user)\n'

const probeOf = async (config: string): Promise<string> => {
  const root: string = projectForTest({
    'defaultAccess.mjs': DEFAULT_ACCESS,
    'payload.config.mjs': `import { defaultAccess } from './defaultAccess.mjs'\n${config}`,
  })
  return await inheritedAccessProbe(path.join(root, 'payload.config.mjs'), path.join(root, 'defaultAccess.mjs'))
}

const decided: string = '{ create: () => false, read: () => false, update: () => false, delete: () => false }'

describe('inheritedAccessProbe', () => {
  it('reports an entity that keeps Payload default access, and one that decides its own', async () => {
    const output: string = await probeOf(
      [
        'export default Promise.resolve({',
        `  collections: [{ slug: 'posts', access: ${decided} }, { slug: 'tags', access: { read: defaultAccess } }],`,
        "  globals: [{ slug: 'header', access: { read: () => true } }],",
        '})',
      ].join('\n'),
    )
    const report: InheritedAccessReport | undefined = parseInheritedAccessReport(output)
    expect(report?.collections).toStrictEqual([
      { slug: 'posts', inherited: [] },
      { slug: 'tags', inherited: ['create', 'read', 'update', 'delete'] },
    ])
    expect(report?.globals).toStrictEqual([{ slug: 'header', inherited: ['update'] }])
  })

  it('asks a drafts-enabled read what it answers a stranger, and reports a throw as undecidable', async () => {
    const output: string = await probeOf(
      [
        'export default {',
        '  collections: [',
        `    { slug: 'open', versions: { drafts: true }, access: { ...${decided}, read: () => true } },`,
        `    { slug: 'filtered', versions: { drafts: true }, access: { ...${decided}, read: () => ({ status: { equals: 'published' } }) } },`,
        `    { slug: 'broken', versions: { drafts: true }, access: { ...${decided}, read: () => { throw new Error('needs a user') } } },`,
        '  ],',
        '  globals: [],',
        '}',
      ].join('\n'),
    )
    const kinds: readonly string[] = (parseInheritedAccessReport(output)?.draftReads ?? []).map(
      (entry): string => entry.anonymousRead.kind,
    )
    expect(kinds).toStrictEqual(['open', 'filtered', 'undecidable'])
  })

  it('reports the resolved authentication settings beside the access', async () => {
    const output: string = await probeOf(
      "export default { collections: [{ slug: 'users', auth: { maxLoginAttempts: 5, lockTime: 600, tokenExpiration: 7200 } }], globals: [] }",
    )
    expect(parsePayloadSecurityReport(output)?.authentication[0]?.slug).toBe('users')
  })

  it('refuses a module whose default export is no built configuration', async () => {
    await expect(probeOf('export default { collections: [] }')).rejects.toThrow('is not a built Payload configuration')
  })

  it("refuses a default-access module that holds no Payload's default access", async () => {
    const root: string = projectForTest({
      'defaultAccess.mjs': 'export const other = 1\n',
      'config.mjs': 'export default {}',
    })
    await expect(
      inheritedAccessProbe(path.join(root, 'config.mjs'), path.join(root, 'defaultAccess.mjs')),
    ).rejects.toThrow("Payload's default access could not be established")
  })
})
