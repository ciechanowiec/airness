import { describe, expect, it } from 'vitest'
import { isHarnessPackage, readHarnessVersions } from '@/harness-packages.js'

describe('the harness packages', () => {
  it('reads the versions the parents name, scoped names included', () => {
    expect(readHarnessVersions('@airness/node=2.0.0, ploaness=1.4.0')).toEqual(
      new Map([
        ['@airness/node', '2.0.0'],
        ['ploaness', '1.4.0'],
      ]),
    )
  })

  it('reads no versions from an unset or malformed value, and none named from a blank one', () => {
    expect(readHarnessVersions(undefined)).toBeUndefined()
    expect(readHarnessVersions('  ')).toEqual(new Map())
    expect(readHarnessVersions('@airness/node')).toBeUndefined()
    expect(readHarnessVersions('@airness/node=')).toBeUndefined()
  })

  it('knows its own packages and no other', () => {
    expect(isHarnessPackage('@airness/node')).toBe(true)
    expect(isHarnessPackage('ploaness')).toBe(true)
    expect(isHarnessPackage('vitest')).toBe(false)
  })
})
