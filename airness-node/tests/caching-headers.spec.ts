import { describe, expect, it } from 'vitest'
import { cachingProblems, type Exchange } from '@/caching-headers.js'

// A GET answered 200 with nothing but the headers a case names.
const answer = (responseHeaders: Readonly<Record<string, string>>, exchange: Partial<Exchange> = {}): Exchange => ({
  method: 'GET',
  status: 200,
  requestHeaders: {},
  responseHeaders,
  ...exchange,
})

const problemsOf = (
  responseHeaders: Readonly<Record<string, string>>,
  exchange: Partial<Exchange> = {},
): readonly string[] => cachingProblems(answer(responseHeaders, exchange))

const MISSING: string = 'cache-control must be declared'
const SETS_COOKIE: string = 'on an answer that sets a cookie'
const AUTHORIZED: string = 'on an answer to a request that carried authorization'

describe('an answer that declares its caching', () => {
  it.each([
    { 'cache-control': 'no-store' },
    { 'Cache-Control': 'private, max-age=0' },
    { 'CACHE-CONTROL': 'public, max-age=31536000, immutable' },
    { 'cache-control': 'no-cache' },
    { 'cache-control': ' max-age = 60 ' },
  ])('passes under any spelling of the header name: %o', (headers: Readonly<Record<string, string>>) => {
    expect(problemsOf(headers)).toEqual([])
  })

  it.each([{}, { 'cache-control': '' }, { 'cache-control': ' , ' }, { 'x-cache-control': 'no-store' }])(
    'fails one that declares nothing: %o',
    (headers: Readonly<Record<string, string>>) => {
      expect(problemsOf(headers)).toEqual([expect.stringContaining(MISSING)])
    },
  )

  it.each([200, 201, 204, 206, 301, 308, 404, 410, 500])('owes a declaration on status %i', (status: number) => {
    expect(problemsOf({}, { status })).toEqual([expect.stringContaining(MISSING)])
  })

  it.each([101, 103, 302, 303, 304, 307])(
    'owes none on status %i, which leaves a cache nothing to guess',
    (status: number) => {
      expect(problemsOf({}, { status })).toEqual([])
    },
  )

  it.each(['OPTIONS', 'options'])('owes none on a preflight sent as %s', (method: string) => {
    expect(problemsOf({}, { method, status: 204 })).toEqual([])
  })

  it('owes one on every other method', () => {
    expect(problemsOf({}, { method: 'POST' })).toEqual([expect.stringContaining(MISSING)])
    expect(problemsOf({}, { method: 'HEAD' })).toEqual([expect.stringContaining(MISSING)])
  })
})

describe('an answer a shared cache may store', () => {
  it.each([
    'public, max-age=60',
    'max-age=60, PUBLIC',
    's-maxage=600',
    'S-MaxAge="600"',
    's-maxage=0',
    'max-age=0,public',
    ' public ,  max-age=60 ',
    'public\nmax-age=60',
  ])('fails when it sets a cookie: %s', (value: string) => {
    expect(problemsOf({ 'cache-control': value, 'set-cookie': 'session=abc; Secure' })).toEqual([
      expect.stringContaining(SETS_COOKIE),
    ])
  })

  it.each(['Authorization', 'authorization', 'AUTHORIZATION'])('fails when its request carried %s', (name: string) => {
    expect(
      problemsOf({ 'cache-control': 'public, max-age=60' }, { requestHeaders: { [name]: 'Bearer token' } }),
    ).toEqual([expect.stringContaining(AUTHORIZED)])
  })

  it('names both causes when both hold', () => {
    expect(
      problemsOf(
        { 'cache-control': 's-maxage=60', 'Set-Cookie': 'session=abc' },
        { requestHeaders: { authorization: 'Basic dXNlcg==' } },
      ),
    ).toEqual([expect.stringContaining(SETS_COOKIE), expect.stringContaining(AUTHORIZED)])
  })

  it.each(['public, no-store', 'NO-STORE, public', 's-maxage=60, no-store', 'public,\nno-store'])(
    'passes when it also says %s',
    (value: string) => {
      expect(
        problemsOf(
          { 'cache-control': value, 'set-cookie': 'session=abc' },
          { requestHeaders: { authorization: 'Bearer token' } },
        ),
      ).toEqual([])
    },
  )

  it('passes a public file whose request only carried the browser cookies', () => {
    expect(
      problemsOf(
        { 'cache-control': 'public, max-age=31536000, immutable' },
        { requestHeaders: { cookie: 'session=abc' } },
      ),
    ).toEqual([])
  })

  it('passes a private answer that sets a cookie for an authorized request', () => {
    expect(
      problemsOf(
        { 'cache-control': 'private, max-age=60', 'set-cookie': 'session=abc' },
        { requestHeaders: { authorization: 'Bearer token' } },
      ),
    ).toEqual([])
  })

  it('ignores an empty Set-Cookie', () => {
    expect(problemsOf({ 'cache-control': 'public, max-age=60', 'set-cookie': ' ' })).toEqual([])
  })

  it('is judged on a status that owes no declaration too', () => {
    expect(problemsOf({ 'cache-control': 'public', 'set-cookie': 'session=abc' }, { status: 302 })).toEqual([
      expect.stringContaining(SETS_COOKIE),
    ])
  })
})

describe('the directives of a cache-control header', () => {
  it('reads several header lines, whether joined by commas, line feeds or kept under two spellings', () => {
    const shared: Readonly<Record<string, string>> = { 'set-cookie': 'session=abc' }
    expect(problemsOf({ ...shared, 'cache-control': 'max-age=60, public' })).toEqual([
      expect.stringContaining(SETS_COOKIE),
    ])
    expect(problemsOf({ ...shared, 'cache-control': 'max-age=60\npublic' })).toEqual([
      expect.stringContaining(SETS_COOKIE),
    ])
    expect(problemsOf({ ...shared, 'Cache-Control': 'max-age=60', 'cache-control': 'public' })).toEqual([
      expect.stringContaining('"max-age=60, public"'),
    ])
    expect(problemsOf({ ...shared, 'Cache-Control': 'public', 'cache-control': 'no-store' })).toEqual([])
  })

  it('does not split a quoted value at its commas', () => {
    expect(
      problemsOf({ 'cache-control': 'private="Set-Cookie, public", max-age=60', 'set-cookie': 'session=abc' }),
    ).toEqual([])
    expect(problemsOf({ 'cache-control': 'no-cache="a\\", public", max-age=60', 'set-cookie': 'session=abc' })).toEqual(
      [],
    )
  })

  it('does not mistake a longer directive name for public or no-store', () => {
    expect(problemsOf({ 'cache-control': 'publicity, max-age=60', 'set-cookie': 'session=abc' })).toEqual([])
    expect(problemsOf({ 'cache-control': 'public, no-store-ish', 'set-cookie': 'session=abc' })).toEqual([
      expect.stringContaining(SETS_COOKIE),
    ])
  })
})
