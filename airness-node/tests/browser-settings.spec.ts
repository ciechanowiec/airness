import { describe, expect, it } from 'vitest'
import { CLOCK_ENVIRONMENT_VARIABLE } from '@/browser-run.js'
import { browserSettings } from '@/browser-settings.js'
import { clockedEnvironment } from '@/clock-environment.js'
import { readSettings } from '@/settings.js'

const fixedPortServer = (fixedPort: unknown): Record<string, unknown> => ({
  auxiliaryServers: [{ command: 'node sibling.js', url: 'http://localhost:4000', fixedPort }],
})

const urlVariablesServer = (urlVariables: unknown): Record<string, unknown> => ({
  auxiliaryServers: [{ command: 'node sibling.js', url: 'http://localhost:4000', urlVariables }],
})

describe('the browser startup inputs', () => {
  it('retains inherited runtime security options alongside the verification clock', () => {
    const fixed: Readonly<Record<string, string>> = clockedEnvironment(
      { settings: readSettings({}) },
      '--no-deprecation',
      '--disable-proto=throw',
    )
    expect(fixed['NODE_OPTIONS']).toContain('--disable-proto=throw --no-deprecation --import=')
    expect(
      clockedEnvironment(
        { settings: readSettings({ airness: { clock: 'real' } }) },
        '--no-deprecation',
        '--disable-proto=throw',
      )['NODE_OPTIONS'],
    ).toBe('--disable-proto=throw --no-deprecation')
  })
  it('keeps complete auxiliary servers and rejects incomplete entries', () => {
    expect(
      browserSettings({
        auxiliaryServers: [
          {
            command: 'storybook dev',
            url: 'http://localhost:6006',
            cwd: '../stories',
          },
          { command: 'server', url: 'http://localhost:4000' },
          { command: 'incomplete' },
          null,
        ],
      }).auxiliaryServers,
    ).toEqual([
      {
        command: 'storybook dev',
        url: 'http://localhost:6006',
        cwd: '../stories',
      },
      { command: 'server', url: 'http://localhost:4000' },
    ])
    expect(browserSettings({}).auxiliaryServers).toEqual([])
  })

  it('takes the variables an application reads its own origin from, once each', () => {
    expect(browserSettings({}).serverUrlVariables).toEqual([])
    expect(
      browserSettings({
        serverUrlVariables: ['NEXT_PUBLIC_SERVER_URL', 'PUBLIC_ORIGIN', 'NEXT_PUBLIC_SERVER_URL'],
      }).serverUrlVariables,
    ).toEqual(['NEXT_PUBLIC_SERVER_URL', 'PUBLIC_ORIGIN'])
  })

  it('refuses an origin variable it could not set, or one the harness sets itself', () => {
    expect(() => browserSettings({ serverUrlVariables: 'NEXT_PUBLIC_SERVER_URL' })).toThrow('must be an array')
    expect(() => browserSettings({ serverUrlVariables: [1] })).toThrow('must be an array')
    expect(() => browserSettings({ serverUrlVariables: ['public-origin'] })).toThrow('"public-origin" is not')
    for (const owned of ['PORT', 'HOSTNAME', 'AIRNESS_SERVER_URL', 'NODE_OPTIONS', 'NEXT_TELEMETRY_DISABLED']) {
      expect(() => browserSettings({ serverUrlVariables: [owned] })).toThrow(`"${owned}" is set by the harness`)
    }
    expect(browserSettings({ serverUrlVariables: ['PORTAL_URL', 'SERVER_HOSTNAME'] }).serverUrlVariables).toHaveLength(
      2,
    )
  })

  it("takes the variables an application reads an auxiliary server's origin from", () => {
    expect(
      browserSettings({
        auxiliaryServers: [
          { command: 'node sibling.js', url: 'http://localhost:4000', urlVariables: ['SIBLING_URL', 'SIBLING_URL'] },
          { command: 'node other.js', url: 'http://localhost:4001', urlVariables: [] },
        ],
      }).auxiliaryServers,
    ).toEqual([
      { command: 'node sibling.js', url: 'http://localhost:4000', urlVariables: ['SIBLING_URL'] },
      { command: 'node other.js', url: 'http://localhost:4001' },
    ])
  })

  it('keeps a fixed port only when it is asked for, and refuses a fixed port that is not a boolean', () => {
    expect(browserSettings(fixedPortServer(true)).auxiliaryServers[0]?.fixedPort).toBe(true)
    expect(browserSettings(fixedPortServer(false)).auxiliaryServers[0]).not.toHaveProperty('fixedPort')
    expect(() => browserSettings(fixedPortServer('yes'))).toThrow('must be true or false')
  })

  it('refuses an auxiliary origin variable it could not set, the harness sets, or another server names', () => {
    expect(() => browserSettings(urlVariablesServer('SIBLING_URL'))).toThrow('urlVariables must be an array')
    expect(() => browserSettings(urlVariablesServer(['sibling-url']))).toThrow('"sibling-url" is not')
    expect(() => browserSettings(urlVariablesServer(['PORT']))).toThrow('"PORT" is set by the harness')
    expect(() =>
      browserSettings({ ...urlVariablesServer(['SHARED_URL']), serverUrlVariables: ['SHARED_URL'] }),
    ).toThrow('"SHARED_URL" is named for more than one server')
  })

  it('preloads the fixed clock and leaves an explicitly real clock alone', () => {
    const fixed: Readonly<Record<string, string>> = clockedEnvironment(
      { settings: readSettings({}) },
      '--no-deprecation',
      '',
    )
    expect(fixed[CLOCK_ENVIRONMENT_VARIABLE]).toBe('2100-01-01T12:00:00.000Z')
    expect(fixed['NODE_OPTIONS']).toContain('--import=file:')
    expect(
      clockedEnvironment({ settings: readSettings({ airness: { clock: 'real' } }) }, '--no-deprecation', ''),
    ).toEqual({
      NODE_OPTIONS: '--no-deprecation',
    })
  })
})
