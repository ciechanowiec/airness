import { describe, expect, it } from 'vitest'
import { type AnsweredRequest, disclosureProblems, securityHeaderProblems } from '@/security-headers.js'

// A page fetched as the security-header spec fetches it.
const GET: AnsweredRequest = { method: 'GET', status: 200, requestHeaders: {} }

const baseline: Readonly<Record<string, string>> = {
  'cache-control': 'private, no-cache',
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
  securityHeaderProblems({ ...production, ...headers }, 'production', GET)

describe('shared HTML header baseline', () => {
  it('accepts the baseline and the default-src fallback', () => {
    expect(securityHeaderProblems(baseline, 'development', GET)).toEqual([])
    expect(
      securityHeaderProblems(
        {
          ...baseline,
          'content-security-policy': "default-src 'self'",
          'x-frame-options': 'DENY',
        },
        'development',
        GET,
      ),
    ).toEqual([])
  })
  it('reports missing decisions and refuses inline script', () => {
    expect(securityHeaderProblems({}, 'development', GET)).toHaveLength(6)
    expect(
      securityHeaderProblems(
        {
          ...baseline,
          'content-security-policy': "script-src 'unsafe-inline'; frame-ancestors 'none'",
        },
        'development',
        GET,
      ),
    ).toEqual([expect.stringContaining('unsafe-inline')])
  })
  it('does not mistake a longer directive name for script-src', () => {
    expect(
      securityHeaderProblems({ ...baseline, 'content-security-policy': "script-src-elem 'self'" }, 'development', GET),
    ).toContain('constrain script through script-src or default-src')
  })
  it('preserves the existing decision-only treatment of an empty source list', () => {
    expect(
      securityHeaderProblems(
        { ...baseline, 'content-security-policy': 'script-src; frame-ancestors' },
        'development',
        GET,
      ),
    ).toEqual([])
  })
})

describe('caching decisions in the baseline', () => {
  it('reports a page that declares no caching, on any server', () => {
    const { 'cache-control': _declared, ...undeclared } = baseline
    expect(securityHeaderProblems(undeclared, 'development', GET)).toEqual([
      expect.stringContaining('cache-control must be declared'),
    ])
    expect(productionProblemsWith({ 'cache-control': '' })).toEqual([
      expect.stringContaining('cache-control must be declared'),
    ])
  })
  it('reports a page a shared cache may store although it was asked with authorization', () => {
    expect(
      securityHeaderProblems({ ...baseline, 'cache-control': 'public, max-age=60' }, 'development', {
        ...GET,
        requestHeaders: { Authorization: 'Bearer token' },
      }),
    ).toEqual([expect.stringContaining('carried authorization')])
  })
  it('reports a page a shared cache may store although it sets a cookie', () => {
    expect(productionProblemsWith({ 'cache-control': 's-maxage=600', 'set-cookie': 'session=abc; Secure' })).toEqual([
      expect.stringContaining('sets a cookie'),
    ])
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
    expect(securityHeaderProblems(without, 'production', GET)).toEqual([
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
    expect(securityHeaderProblems(baseline, 'development', GET)).toEqual([])
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
    expect(securityHeaderProblems({ ...baseline, 'set-cookie': 'session=def; Path=/' }, 'development', GET)).toEqual([])
  })
})

describe('headers that name the software behind a response', () => {
  it.each(['Next.js', 'Next.js, Express', 'Express', 'PHP/8.3.1', ''])(
    'refuses x-powered-by: "%s"',
    (value: string) => {
      expect(disclosureProblems({ 'x-powered-by': value })).toEqual([
        expect.stringContaining(`x-powered-by is "${value}"`),
      ])
    },
  )
  it.each([
    'nginx/1.25.3',
    'Apache/2.4.58 (Unix)',
    'Apache-Coyote/1.1',
    'Microsoft-IIS/10.0',
    'Jetty(12.0.5)',
    'Caddy/v2',
  ])('refuses a server header that names a version: %s', (value: string) => {
    expect(disclosureProblems({ server: value })).toEqual([expect.stringContaining(`server is "${value}"`)])
  })
  it.each(['nginx', 'cloudflare', 'Apache', 'h2o', 'gunicorn (Unix)'])(
    'accepts a server header that names no version: %s',
    (value: string) => {
      expect(disclosureProblems({ server: value })).toEqual([])
    },
  )
  it('holds a development server to it as well as the production one', () => {
    const disclosing: Readonly<Record<string, string>> = { 'x-powered-by': 'Next.js' }
    expect(securityHeaderProblems({ ...baseline, ...disclosing }, 'development', GET)).toEqual([
      expect.stringContaining('x-powered-by is "Next.js"'),
    ])
    expect(productionProblemsWith(disclosing)).toEqual([expect.stringContaining('x-powered-by is "Next.js"')])
  })
})
