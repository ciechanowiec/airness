import { describe, expect, it } from 'vitest'
import { securityHeaderProblems } from '@/security-headers.js'

const baseline: Readonly<Record<string, string>> = {
  'x-content-type-options': 'nosniff',
  'referrer-policy': 'strict-origin-when-cross-origin',
  'content-security-policy': "script-src 'self'; frame-ancestors 'none'",
}

// What the production server sends on top of the baseline: two years of transport security, as the seeded proxy
// sends it.
const production: Readonly<Record<string, string>> = {
  ...baseline,
  'strict-transport-security': 'max-age=63072000; includeSubDomains',
}

const productionProblemsWith = (headers: Readonly<Record<string, string>>): readonly string[] =>
  securityHeaderProblems({ ...production, ...headers }, 'production')

describe('shared HTML header baseline', () => {
  it('accepts the baseline and the default-src fallback', () => {
    expect(securityHeaderProblems(baseline, 'development')).toEqual([])
    expect(
      securityHeaderProblems(
        {
          ...baseline,
          'content-security-policy': "default-src 'self'",
          'x-frame-options': 'DENY',
        },
        'development',
      ),
    ).toEqual([])
  })
  it('reports missing decisions and refuses inline script', () => {
    expect(securityHeaderProblems({}, 'development')).toHaveLength(5)
    expect(
      securityHeaderProblems(
        {
          ...baseline,
          'content-security-policy': "script-src 'unsafe-inline'; frame-ancestors 'none'",
        },
        'development',
      ),
    ).toEqual([expect.stringContaining('unsafe-inline')])
  })
  it('does not mistake a longer directive name for script-src', () => {
    expect(
      securityHeaderProblems({ ...baseline, 'content-security-policy': "script-src-elem 'self'" }, 'development'),
    ).toContain('constrain script through script-src or default-src')
  })
  it('preserves the existing decision-only treatment of an empty source list', () => {
    expect(
      securityHeaderProblems({ ...baseline, 'content-security-policy': 'script-src; frame-ancestors' }, 'development'),
    ).toEqual([])
  })
})

describe('transport security of the production server', () => {
  it('accepts a lifetime of a year or more, however its directives are spelled', () => {
    const spellings: readonly string[] = [
      'max-age=63072000; includeSubDomains',
      'max-age=31536000',
      'MAX-AGE=31536000',
      'includeSubDomains; max-age="31536000"; preload',
      ' max-age = 31536000 ',
    ]
    expect(
      spellings.map((value: string): readonly string[] =>
        productionProblemsWith({ 'strict-transport-security': value }),
      ),
    ).toEqual(spellings.map((): readonly string[] => []))
  })
  it('refuses a missing header', () => {
    const { 'strict-transport-security': _sent, ...without } = production
    expect(securityHeaderProblems(without, 'production')).toEqual([
      expect.stringContaining('strict-transport-security must be declared'),
    ])
  })
  it.each([
    'max-age=31535999',
    'max-age=0',
    'includeSubDomains',
    'max-age',
    'max-age=one-year',
    'max-age=31536000; max-age=63072000',
  ])('refuses a short lifetime or a header a browser ignores: %s', (value: string) => {
    expect(productionProblemsWith({ 'strict-transport-security': value })).toEqual([
      expect.stringContaining(`"${value}"`),
    ])
  })
  it('demands nothing of a development server, which may answer on a developer machine', () => {
    expect(securityHeaderProblems(baseline, 'development')).toEqual([])
  })
})

describe('cookies the production server sets', () => {
  it('accepts every cookie marked Secure, however the attribute is spelled', () => {
    const cookies: string = [
      'session-token=abc; Path=/; HttpOnly; Secure; SameSite=Lax',
      'JSESSIONID=def; Path=/; secure; HttpOnly',
      'locale=en; SECURE',
      'theme=dark; Secure=yes; Expires=Wed, 21 Oct 2099 07:28:00 GMT',
    ].join('\n')
    expect(productionProblemsWith({ 'set-cookie': cookies })).toEqual([])
  })
  it('names each cookie set without Secure, and only those', () => {
    const cookies: string = [
      'session-token=abc; Path=/; HttpOnly; SameSite=Lax',
      'session=def; Path=/; Secure',
      'insecure-name=ghi; Path=/secure',
      'flag',
    ].join('\n')
    expect(productionProblemsWith({ 'set-cookie': cookies })).toEqual([
      expect.stringContaining('cookie "session-token" is set without Secure'),
      expect.stringContaining('cookie "insecure-name" is set without Secure'),
      expect.stringContaining('cookie "flag" is set without Secure'),
    ])
  })
  it('leaves a development server to send its cookies over plain HTTP', () => {
    expect(securityHeaderProblems({ ...baseline, 'set-cookie': 'session=def; Path=/' }, 'development')).toEqual([])
  })
})
