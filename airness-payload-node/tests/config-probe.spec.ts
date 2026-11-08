import path from 'node:path'
import type { GateResult } from '@airness/node/gate-result'
import { describe, expect, it } from 'vitest'
import { payloadConfigFileOf } from '@/config-probe.js'
import { REQUIRED_PINS } from '@/payload-pins.js'
import { payloadMemberAt, projectForTest } from './helpers/temporary-project.js'

const PAYLOAD_MANIFEST: unknown = { name: 'site', dependencies: { ...REQUIRED_PINS } }

const located = (
  files: Readonly<Record<string, string>>,
): { readonly root: string; readonly found: string | GateResult } => {
  const root: string = projectForTest(files)
  return { root, found: payloadConfigFileOf(payloadMemberAt(root, PAYLOAD_MANIFEST)) }
}

const findingsOf = (found: string | GateResult): string =>
  typeof found === 'string' || found.ok ? '' : found.findings.join('\n')

describe('payloadConfigFileOf', () => {
  it('returns the configuration the tsconfig alias names', () => {
    const { root, found } = located({
      'tsconfig.json': JSON.stringify({ compilerOptions: { paths: { '@payload-config': ['./config/cms.ts'] } } }),
      'config/cms.ts': 'export default {}',
    })
    expect(found).toBe(path.join(root, 'config/cms.ts'))
  })

  it('fails naming the configuration the tsconfig points at when that file is absent', () => {
    const { root, found } = located({
      'tsconfig.json': JSON.stringify({ compilerOptions: { paths: { '@payload-config': ['./config/cms.ts'] } } }),
    })
    expect(findingsOf(found)).toContain(`no configuration at ${path.join(root, 'config/cms.ts')}`)
  })

  it('fails naming the tsconfig when the member has none', () => {
    const { found } = located({ 'src/payload.config.ts': 'export default {}' })
    expect(findingsOf(found)).toContain('no configuration at tsconfig.json')
  })
})
