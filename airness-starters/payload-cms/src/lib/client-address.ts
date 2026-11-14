// Which client a request comes from, as far as limiting its authentication attempts needs to know.
//
// A Next.js route handler sees no socket, only headers. The server fills `X-Forwarded-For` from the socket when a
// request arrives without one, but keeps one a client sent, so the header is only as trustworthy as the proxies that
// wrote it. A reverse proxy that appends the address it received the request from is trustworthy for its own entry
// and for nothing before it. The client address is therefore the entry as many places from the end as there are
// trusted proxies: anything a client wrote further left is ignored.
//
// An IPv6 client usually holds a whole /64 network and can pick a new address in it for every request, so an IPv6
// address is reduced to its /64 network: limiting the address alone would limit nothing.
import { isIPv4, isIPv6 } from 'node:net'

const IPV6_GROUPS: number = 8
// An address holds at most one `::`, so it splits into the groups before it and the groups after it.
const ZERO_RUN_SIDES: number = 2
const NETWORK_GROUPS: number = 4
const IPV4_MAPPED_PREFIX: string = '::ffff:'
const BRACKETED_IPV6: RegExp = /^\[(?<address>[^\]]+)\](?::\d+)?$/u
const IPV4_WITH_PORT: RegExp = /^(?<address>[\d.]+):\d+$/u

// Some proxies write a port after the address, bracketing an IPv6 address to set the port apart.
const withoutPort = (entry: string): string =>
  BRACKETED_IPV6.exec(entry)?.groups?.['address'] ?? IPV4_WITH_PORT.exec(entry)?.groups?.['address'] ?? entry

// The eight groups of an IPv6 address, with the run of zero groups that `::` stands for written out.
const ipv6Groups = (address: string): readonly string[] => {
  const [head = '', tail]: readonly (string | undefined)[] = address.split('::', ZERO_RUN_SIDES)
  const leading: readonly string[] = head === '' ? [] : head.split(':')
  if (tail === undefined) {
    return leading
  }
  const trailing: readonly string[] = tail === '' ? [] : tail.split(':')
  const zeros: readonly string[] = Array.from({ length: IPV6_GROUPS - leading.length - trailing.length }, () => '0')
  return [...leading, ...zeros, ...trailing]
}

// The key a client's attempts are counted under: an IPv4 address as it is, an IPv6 address as its /64 network.
const clientKeyOf = (address: string): string | undefined => {
  const lowered: string = address.toLowerCase()
  if (lowered.startsWith(IPV4_MAPPED_PREFIX) && isIPv4(lowered.slice(IPV4_MAPPED_PREFIX.length))) {
    return lowered.slice(IPV4_MAPPED_PREFIX.length)
  }
  if (isIPv4(lowered)) {
    return lowered
  }
  if (!isIPv6(lowered)) {
    return undefined
  }
  const network: string = ipv6Groups(lowered)
    .slice(0, NETWORK_GROUPS)
    .map((group: string): string => group.replace(/^0+(?=.)/u, ''))
    .join(':')
  return `${network}::/64`
}

/**
 * The key of the client a request comes from, read from the `X-Forwarded-For` header.
 * @param forwardedFor the header's value, or null when the request carries none.
 * @param trustedProxyCount how many reverse proxies in front of the server append to the header.
 * @returns the client's key, or undefined when the header holds fewer entries than there are trusted proxies or the
 *   trusted entry is no IP address.
 */
export const forwardedClientKey = (forwardedFor: string | null, trustedProxyCount: number): string | undefined => {
  const entries: readonly string[] = (forwardedFor ?? '')
    .split(',')
    .map((entry: string): string => entry.trim())
    .filter((entry: string): boolean => entry.length > 0)
  const trusted: string | undefined = entries.at(-trustedProxyCount)
  return trusted === undefined ? undefined : clientKeyOf(withoutPort(trusted))
}
