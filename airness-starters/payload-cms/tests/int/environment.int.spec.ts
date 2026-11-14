import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { isDevelopment, loadServerEnvironment, type ServerEnvironment } from '@/lib/environment'

// A value guaranteed non-empty after trimming: prefixing a non-space char makes `trim()` keep it.
const nonEmpty: fc.Arbitrary<string> = fc.string().map((suffix: string): string => `x${suffix}`)

// Build an env source from [name, value] pairs. The names are passed as string literals (not object
// keys) so the SCREAMING_CASE environment-variable names stay clear of the camelCase naming rules.
const bareEnvironmentFrom = (pairs: [string, string][]): ServerEnvironment =>
  loadServerEnvironment(Object.fromEntries(pairs))

// A complete upload storage configuration, so the cases about the other variables judge only those.
const STORAGE: [string, string][] = [
  ['S3_ENDPOINT', 'http://127.0.0.1:9000'],
  ['S3_BUCKET', 'uploads'],
  ['S3_ACCESS_KEY_ID', 'access'],
  ['S3_SECRET_ACCESS_KEY', 'storage-key'],
]

const environmentFrom = (pairs: [string, string][]): ServerEnvironment => bareEnvironmentFrom([...STORAGE, ...pairs])

const SECRET: [string, string] = ['PAYLOAD_SECRET', 's']
const DATABASE: [string, string] = ['DATABASE_URL', 'd']

// The message a call rejects with, as a value. These specs assert the exact wording, so the throw has
// to be caught rather than matched by pattern.
const messageFrom = (call: () => unknown): string => {
  try {
    call()
  } catch (error: unknown) {
    return error instanceof Error ? error.message : String(error)
  }
  return ''
}

describe('loadServerEnvironment', () => {
  it('returns every field when the source is complete and valid', () => {
    const environment: ServerEnvironment = environmentFrom([
      ['PAYLOAD_SECRET', 'a-secret'],
      ['DATABASE_URL', 'postgres://u:p@localhost:5432/db'],
      ['NEXT_PUBLIC_SERVER_URL', 'https://example.com'],
    ])
    expect(environment).toEqual({
      payloadSecret: 'a-secret',
      databaseUrl: 'postgres://u:p@localhost:5432/db',
      serverUrl: 'https://example.com',
      storage: {
        endpoint: 'http://127.0.0.1:9000',
        bucket: 'uploads',
        region: 'us-east-1',
        accessKeyId: 'access',
        secretAccessKey: 'storage-key',
      },
      trustedProxyCount: 1,
    })
  })

  it('defaults the server URL to localhost when NEXT_PUBLIC_SERVER_URL is unset', () => {
    const environment: ServerEnvironment = environmentFrom([SECRET, DATABASE])
    expect(environment.serverUrl).toBe('http://localhost:3000')
  })

  it('defaults the server URL when NEXT_PUBLIC_SERVER_URL is blank', () => {
    const environment: ServerEnvironment = environmentFrom([
      SECRET,
      DATABASE,
      ['NEXT_PUBLIC_SERVER_URL', ' '.repeat(3)],
    ])
    expect(environment.serverUrl).toBe('http://localhost:3000')
  })
})

// A production build has no development server to fall back to, so it must be told its public origin.
describe('the server URL of a production build', () => {
  const Production: [string, string] = ['NODE_ENV', 'production']

  it('is required, rather than defaulted to localhost', () => {
    expect(messageFrom(() => environmentFrom([SECRET, DATABASE, Production]))).toBe(
      'Invalid environment configuration:\n  - NEXT_PUBLIC_SERVER_URL is required and must be non-empty.',
    )
  })

  it('is the one NEXT_PUBLIC_SERVER_URL names', () => {
    const environment: ServerEnvironment = environmentFrom([
      SECRET,
      DATABASE,
      Production,
      ['NEXT_PUBLIC_SERVER_URL', 'https://cms.example.com'],
    ])
    expect(environment.serverUrl).toBe('https://cms.example.com')
  })
})

// How many reverse proxies in front of the server the client address is read behind.
describe('the trusted proxy count', () => {
  const TwoProxies: number = 2

  it('is the whole number TRUSTED_PROXY_COUNT names', () => {
    const proxies: [string, string] = ['TRUSTED_PROXY_COUNT', String(TwoProxies)]
    expect(environmentFrom([SECRET, DATABASE, proxies]).trustedProxyCount).toBe(TwoProxies)
  })

  it.each(['0', '-1', '1.5', 'two'])('rejects TRUSTED_PROXY_COUNT set to %s', (count: string) => {
    expect(messageFrom(() => environmentFrom([SECRET, DATABASE, ['TRUSTED_PROXY_COUNT', count]]))).toBe(
      'Invalid environment configuration:\n  - TRUSTED_PROXY_COUNT must be a whole number of at least 1 when set.',
    )
  })
})

// The other half of the same question: what the parser rejects, and exactly how it says so.
describe('loadServerEnvironment rejections', () => {
  it('throws with exactly one bullet when only PAYLOAD_SECRET is missing', () => {
    const message: string = messageFrom(() => environmentFrom([DATABASE]))
    // Exact, single-issue message: a valid field contributes NO bullet. This pins the `.filter`
    // that drops non-issue fields - without it, valid fields would add `  - undefined` lines.
    const expected: string = [
      'Invalid environment configuration:',
      '  - PAYLOAD_SECRET is required and must be non-empty.',
    ].join('\n')
    expect(message).toBe(expected)
  })

  it('throws and names DATABASE_URL when it is missing', () => {
    expect(() => environmentFrom([SECRET])).toThrow(/DATABASE_URL/)
  })

  it('treats a whitespace-only required value as missing', () => {
    expect(() => environmentFrom([['PAYLOAD_SECRET', ' '.repeat(3)], DATABASE])).toThrow(/PAYLOAD_SECRET/)
  })

  it('rejects a NEXT_PUBLIC_SERVER_URL that is set but not a valid URL', () => {
    expect(() => environmentFrom([SECRET, DATABASE, ['NEXT_PUBLIC_SERVER_URL', 'not a url']])).toThrow(
      /NEXT_PUBLIC_SERVER_URL/,
    )
  })

  it('aggregates every problem into one exactly-formatted error rather than failing on the first', () => {
    const message: string = messageFrom(() => environmentFrom([['NEXT_PUBLIC_SERVER_URL', 'nope']]))
    // Asserting the EXACT message (not just `toContain` the var names) pins the formatting - the
    // prefix, the `  - ` bullet, the `\n` separator, and each issue's wording - so a regression that
    // changes any of those static strings is caught (it would otherwise survive a contains-check).
    const expected: string = [
      'Invalid environment configuration:',
      '  - PAYLOAD_SECRET is required and must be non-empty.',
      '  - DATABASE_URL is required and must be non-empty.',
      '  - NEXT_PUBLIC_SERVER_URL must be a valid URL when set.',
    ].join('\n')
    expect(message).toBe(expected)
  })
})

describe('loadServerEnvironment properties (fast-check)', () => {
  it('preserves the secret and database, and defaults the server URL, for any non-empty inputs', () => {
    fc.assert(
      fc.property(nonEmpty, nonEmpty, (secret: string, database: string): boolean => {
        const environment: ServerEnvironment = environmentFrom([
          ['PAYLOAD_SECRET', secret],
          ['DATABASE_URL', database],
        ])
        return (
          environment.payloadSecret === secret &&
          environment.databaseUrl === database &&
          environment.serverUrl === 'http://localhost:3000'
        )
      }),
    )
  })

  it('preserves any valid web URL as the server URL', () => {
    fc.assert(
      fc.property(
        nonEmpty,
        nonEmpty,
        fc.webUrl(),
        (secret: string, database: string, url: string): boolean =>
          environmentFrom([
            ['PAYLOAD_SECRET', secret],
            ['DATABASE_URL', database],
            ['NEXT_PUBLIC_SERVER_URL', url],
          ]).serverUrl === url,
      ),
    )
  })

  it('always throws (naming the key) when a required variable is absent', () => {
    fc.assert(
      fc.property(nonEmpty, (database: string): boolean => {
        try {
          environmentFrom([['DATABASE_URL', database]])
          return false
        } catch (error: unknown) {
          return error instanceof Error && error.message.includes('PAYLOAD_SECRET')
        }
      }),
    )
  })
})

describe('loadServerEnvironment on the ambient environment', () => {
  it('reads and validates the ambient process environment (populated by dotenv in setup)', () => {
    const environment: ServerEnvironment = loadServerEnvironment()
    expect(environment.payloadSecret.length).toBeGreaterThan(0)
    expect(environment.databaseUrl.length).toBeGreaterThan(0)
  })
})

// The proxy (`src/proxy.ts`) asks this on every request to decide one CSP directive, so what it answers under the
// suite's own NODE_ENV is the behaviour worth pinning: anything that is not a production build is
// development, including NODE_ENV being unset entirely.
describe('isDevelopment', () => {
  it('reports development while the suite runs, which is not a production build', () => {
    expect(isDevelopment()).toBe(true)
  })
})

describe('upload storage environment', () => {
  it('signs for the region S3_REGION names', () => {
    expect(environmentFrom([SECRET, DATABASE, ['S3_REGION', 'eu-central-1']]).storage.region).toBe('eu-central-1')
  })

  it('names every missing storage variable, and only those, when none is set', () => {
    const message: string = messageFrom(() => bareEnvironmentFrom([SECRET, DATABASE]))
    const expected: string = [
      'Invalid environment configuration:',
      '  - S3_ENDPOINT is required and must be non-empty.',
      '  - S3_BUCKET is required and must be non-empty.',
      '  - S3_ACCESS_KEY_ID is required and must be non-empty.',
      '  - S3_SECRET_ACCESS_KEY is required and must be non-empty.',
    ].join('\n')
    expect(message).toBe(expected)
  })

  it('rejects an S3_ENDPOINT that is not a URL', () => {
    expect(messageFrom(() => environmentFrom([SECRET, DATABASE, ['S3_ENDPOINT', 'not a url']]))).toBe(
      'Invalid environment configuration:\n  - S3_ENDPOINT must be a valid URL.',
    )
  })
})
