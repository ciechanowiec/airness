import { describe, expect, it } from 'vitest'
import { CLOCK_ENVIRONMENT_VARIABLE, DEFAULT_CLOCK_INSTANT } from '@/browser-run.js'
import { browserSettings } from '@/browser-settings.js'
import { clockedEnvironment } from '@/clock-environment.js'
import { readSettings } from '@/settings.js'

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

  it('preloads the fixed clock and leaves an explicitly real clock alone', () => {
    const fixed: Readonly<Record<string, string>> = clockedEnvironment(
      { settings: readSettings({}) },
      '--no-deprecation',
      '',
    )
    expect(fixed[CLOCK_ENVIRONMENT_VARIABLE]).toBe(DEFAULT_CLOCK_INSTANT)
    expect(fixed['NODE_OPTIONS']).toContain('--import=file:')
    expect(
      clockedEnvironment({ settings: readSettings({ airness: { clock: 'real' } }) }, '--no-deprecation', ''),
    ).toEqual({
      NODE_OPTIONS: '--no-deprecation',
    })
  })
})
