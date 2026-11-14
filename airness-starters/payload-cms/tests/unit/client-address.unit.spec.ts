import { describe, expect, it } from 'vitest'
import { forwardedClientKey } from '@/lib/client-address'

/** An address of the IPv4 documentation range, which no real client holds. */
const CLIENT_IPV4: string = '203.0.113.9'

const ONE_PROXY: number = 1
const TWO_PROXIES: number = 2

describe('the client a request comes from', () => {
  it('is the entry the one trusted proxy appended, whatever the client wrote before it', () => {
    expect(forwardedClientKey('198.51.100.7, 203.0.113.9', ONE_PROXY)).toBe('203.0.113.9')
  })

  it('is the entry the outermost of two trusted proxies appended', () => {
    expect(forwardedClientKey('198.51.100.7, 203.0.113.9, 192.0.2.1', TWO_PROXIES)).toBe('203.0.113.9')
  })

  it.each([
    { header: null, state: 'no header' },
    { header: '', state: 'an empty header' },
    { header: '203.0.113.9', state: 'fewer entries than trusted proxies' },
  ])('is unknown from $state', ({ header }) => {
    expect(forwardedClientKey(header, TWO_PROXIES)).toBeUndefined()
  })

  it('is unknown when the trusted entry is no IP address', () => {
    expect(forwardedClientKey('unknown', ONE_PROXY)).toBeUndefined()
  })

  it.each([
    { entry: '203.0.113.9:51234', key: '203.0.113.9' },
    { entry: '[2001:db8:1:2::7]:443', key: '2001:db8:1:2::/64' },
  ])('ignores the port a proxy wrote after $entry', ({ entry, key }) => {
    expect(forwardedClientKey(entry, ONE_PROXY)).toBe(key)
  })
})

// The key of a client the one trusted proxy names.
const keyOf = (address: string): string | undefined => forwardedClientKey(address, ONE_PROXY)

describe('the key a client is counted under', () => {
  it('is an IPv4 address as it is', () => {
    expect(keyOf(CLIENT_IPV4)).toBe(CLIENT_IPV4)
  })

  it('is the IPv4 address an IPv4-mapped IPv6 address carries', () => {
    expect(keyOf(`::ffff:${CLIENT_IPV4}`)).toBe(CLIENT_IPV4)
  })

  it.each([
    { address: '2001:db8:1:2:aaaa:bbbb:cccc:dddd', state: 'a full address' },
    { address: '2001:db8:0001:0002::7', state: 'an address with leading zeros' },
    { address: '2001:db8:1:2::', state: 'an address ending in its zero run' },
  ])('is the /64 network of $state', ({ address }) => {
    expect(keyOf(address)).toBe('2001:db8:1:2::/64')
  })

  it('writes out the zero groups the zero run hides inside the network', () => {
    expect(keyOf('2001:db8::7')).toBe('2001:db8:0:0::/64')
  })

  it('reads an address written in capitals as the same client', () => {
    expect(keyOf('2001:db8:1:2::7'.toUpperCase())).toBe('2001:db8:1:2::/64')
  })

  it('is absent for text that is no IP address', () => {
    expect(keyOf('not-an-address')).toBeUndefined()
  })
})
