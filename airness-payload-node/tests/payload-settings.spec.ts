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

  it('reads a malformed block as the defaults', () => {
    expect(readPayloadSettings({ airnessPayload: { publicAccess: 'posts' } }).publicAccess).toStrictEqual([])
  })
})
