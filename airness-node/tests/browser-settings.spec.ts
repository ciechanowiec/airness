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
          { command: 'storybook dev', url: 'http://localhost:6006', cwd: '../stories' },
          { command: 'server', url: 'http://localhost:4000' },
          { command: 'incomplete' },
          null,
        ],
      }).auxiliaryServers,
    ).toEqual([
      { command: 'storybook dev', url: 'http://localhost:6006', cwd: '../stories' },
      { command: 'server', url: 'http://localhost:4000' },
    ])
    expect(browserSettings({}).auxiliaryServers).toEqual([])
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
