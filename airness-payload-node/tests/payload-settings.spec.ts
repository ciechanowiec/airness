import { readFileSync } from 'node:fs'
import { asRecord, readKey } from '@airness/node/json-shapes'
import { describe, expect, it } from 'vitest'
import { type PayloadSettings, readPayloadSettings } from '@/payload-settings.js'

describe('readPayloadSettings', () => {
  it('grants no anonymous permission and supplies the analysis placeholders by default', () => {
    const settings: PayloadSettings = readPayloadSettings({})
    expect(settings.publicAccess).toStrictEqual([])
    expect(
      Object.keys(settings.analysisEnv).toSorted((left: string, right: string): number => left.localeCompare(right)),
    ).toStrictEqual(['DATABASE_URL', 'PAYLOAD_SECRET'])
  })

  it('reads the grants a project declares with their reasons and fields', () => {
    const settings: PayloadSettings = readPayloadSettings({
      airnessPayload: {
        publicAccess: [{ entity: 'posts', operation: 'read', reason: 'the published blog', fields: ['title', 7] }],
      },
    })
    expect(settings.publicAccess).toStrictEqual([
      { entity: 'posts', operation: 'read', reason: 'the published blog', fields: ['title'] },
    ])
  })

  it('drops a grant that names no reason, which restores the finding', () => {
    const settings: PayloadSettings = readPayloadSettings({
      airnessPayload: { publicAccess: [{ entity: 'posts', operation: 'read' }, 'posts', null] },
    })
    expect(settings.publicAccess).toStrictEqual([])
  })

  it('adds the analysis variables a project declares to the placeholders', () => {
    const settings: PayloadSettings = readPayloadSettings({
      airnessPayload: { analysisEnv: { SMTP_HOST: 'localhost', BROKEN: 1 } },
    })
    expect(settings.analysisEnv['SMTP_HOST']).toBe('localhost')
    expect(settings.analysisEnv['BROKEN']).toBeUndefined()
    expect(settings.analysisEnv['PAYLOAD_SECRET']).toBeDefined()
  })

  // The node gates read the placeholders from the layer's manifest, the Payload gates from these settings, so
  // knip and payload-defaults load the configuration in the same environment.
  it('names the same analysis placeholders the package manifest hands the node gates', () => {
    const manifest: unknown = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'))
    const seam: unknown = readKey(readKey(readKey(manifest, 'airness'), 'layer'), 'analysisEnvironment')
    expect(asRecord(readKey(seam, 'defaults'))).toStrictEqual(readPayloadSettings({}).analysisEnv)
    expect(readKey(seam, 'projectSettings')).toBe('airnessPayload')
  })

  it('reads a malformed block as the defaults', () => {
    expect(readPayloadSettings({ airnessPayload: { publicAccess: 'posts' } }).publicAccess).toStrictEqual([])
  })
})
