// The caching decisions every answer of the application owes, judged from the exchange's own headers: whether it
// declares its caching at all, and whether a shared cache, such as a CDN or a proxy, may store an answer that
// belongs to one visitor. Any server is held to them, a development server included, because a missing decision
// is the same defect wherever it is made.
//
// What headers alone cannot tell is left out: an answer personal only through the cookie its request carried,
// such as a "current user" endpoint, looks exactly like a public file, and a browser sends its cookies with every
// asset, so judging a request's cookie would fail correctly cached static files.

/** One answered request: what was asked, and what came back. Header names may be written in any case. */
export interface Exchange {
  /** The request method. */
  readonly method: string
  /** The answer's status code. */
  readonly status: number
  /** The headers the request carried. */
  readonly requestHeaders: Readonly<Record<string, string>>
  /** The headers of the answer, with several lines of one header joined by commas or line feeds. */
  readonly responseHeaders: Readonly<Record<string, string>>
}

const CACHE_CONTROL: string = 'cache-control'

const FIRST_FINAL_STATUS: number = 200
const FOUND: number = 302
const SEE_OTHER: number = 303
const NOT_MODIFIED: number = 304
const TEMPORARY_REDIRECT: number = 307

// The answers that leave a cache nothing to guess without Cache-Control: the temporary redirects RFC 9111 never
// lets a cache store without explicit freshness, and Not Modified, which only refreshes a stored answer that keeps
// the caching decision it was stored with (RFC 9111 section 4.3.4).
const UNGUESSED_STATUSES: ReadonlySet<number> = new Set<number>([FOUND, SEE_OTHER, NOT_MODIFIED, TEMPORARY_REDIRECT])

// Every value a header carries under any spelling of its name, in the order given. HTTP compares header names
// without case, and a record may hold two spellings of one header when its lines came from different sources.
const valuesOf = (headers: Readonly<Record<string, string>>, name: string): readonly string[] =>
  Object.entries(headers)
    .filter(([key]: readonly [string, string]): boolean => key.toLowerCase() === name)
    .map(([, value]: readonly [string, string]): string => value)

// RFC 9111 directives: `name[=value]` separated by commas, names compared without case, a value either a token or
// a quoted string that may hold commas and backslash-escaped quotes of its own, as in
// `no-cache="Set-Cookie, Authorization"`. Several header lines join with commas, or with line feeds where a client
// keeps them apart. A quoted string left open runs to the end of the value.
const DIRECTIVE: RegExp = /(?:[^",\n]|"(?:[^"\\]|\\.)*(?:"|$))+/gu

const directiveNames = (value: string): readonly string[] =>
  (value.match(DIRECTIVE) ?? [])
    .map((directive: string): string => directive.split('=', 1).join('').trim().toLowerCase())
    .filter((name: string): boolean => name.length > 0)

// An OPTIONS answer is a preflight no cache serves to a page, an informational answer is no answer yet, and the
// rest of the unguessed statuses carry nothing a cache keeps on its own.
const requiresDeclaration = (exchange: Exchange): boolean =>
  exchange.method.toUpperCase() !== 'OPTIONS' &&
  exchange.status >= FIRST_FINAL_STATUS &&
  !UNGUESSED_STATUSES.has(exchange.status)

const DECLARATION_REPAIR: string =
  'declare cache-control on every answer, such as no-store for one that belongs to a visitor, or public, ' +
  'max-age=31536000, immutable for a file whose address changes with its content; without it a CDN applies its ' +
  'own default lifetime and a browser guesses one from last-modified'

const SHARED_REPAIR: string =
  'add no-store, or replace public and s-maxage with private, so that no shared cache stores it'

// What makes an answer a shared cache may store personal: a cookie it sets, which the cache would replay to every
// visitor, or the authorization its request carried, which only public and s-maxage let a shared cache store
// (RFC 9111 section 3.5). A cookie the request carried is no sign: a browser sends its cookies with every asset.
const personalProblems = (exchange: Exchange, value: string): readonly string[] => [
  ...(valuesOf(exchange.responseHeaders, 'set-cookie').some((cookie: string): boolean => cookie.trim().length > 0)
    ? [
        `${CACHE_CONTROL} is "${value}" on an answer that sets a cookie, so a shared cache would hand that cookie ` +
          `to every visitor; ${SHARED_REPAIR}`,
      ]
    : []),
  ...(valuesOf(exchange.requestHeaders, 'authorization').length > 0
    ? [
        `${CACHE_CONTROL} is "${value}" on an answer to a request that carried authorization, so a shared cache ` +
          `would hand one visitor's answer to every other; ${SHARED_REPAIR}`,
      ]
    : []),
]

/**
 * Judge the caching decisions of one answer of the application. The caller passes only answers of the application
 * under test, never those of another origin, such as a font or an analytics script a page loads.
 * @param exchange the request and its answer.
 * @returns one problem per missing or dangerous decision, or nothing.
 */
export const cachingProblems = (exchange: Exchange): readonly string[] => {
  const declared: readonly string[] = valuesOf(exchange.responseHeaders, CACHE_CONTROL)
  const names: ReadonlySet<string> = new Set<string>(
    declared.flatMap((value: string): readonly string[] => directiveNames(value)),
  )
  const isShared: boolean = (names.has('public') || names.has('s-maxage')) && !names.has('no-store')
  return [
    ...(names.size === 0 && requiresDeclaration(exchange)
      ? [`${CACHE_CONTROL} must be declared; ${DECLARATION_REPAIR}`]
      : []),
    ...(isShared ? personalProblems(exchange, declared.join(', ')) : []),
  ]
}
