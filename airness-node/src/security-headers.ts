// The browser baseline shared by the security-header spec, the sweep's checks of individual HTML responses and a
// project's own specifications.
//
// Two of its requirements bind the production server alone, because each would harm a developer's machine or be
// impossible there. A browser that receives Strict-Transport-Security over HTTPS remembers it for every port of
// that host, so a development server sending it on a developer's `https://localhost` would hold every other
// application on that machine to HTTPS for the length of max-age. And a development server answers over plain
// HTTP, where a session cookie a project marks Secure in production only is legitimately sent without it. The
// verification run serves the production build, or the declared `serve` script with NODE_ENV=production, so it
// is where both are judged.
import type { ServedBy } from './browser-server-policy.js'

const directive = (policy: string, name: string): string | undefined =>
  policy
    .split(';')
    .map((part: string): string => part.trim())
    .find((part: string): boolean => part === name || part.startsWith(`${name} `))

const scriptProblems = (policy: string): readonly string[] => {
  const sources: string | undefined = directive(policy, 'script-src') ?? directive(policy, 'default-src')
  return [
    ...(policy.length === 0 ? ['content-security-policy must be declared'] : []),
    ...(sources === undefined ? ['constrain script through script-src or default-src'] : []),
    ...(sources?.includes("'unsafe-inline'") === true ? ["script sources must not allow 'unsafe-inline'"] : []),
  ]
}

/**
 * The shortest Strict-Transport-Security lifetime the production server may declare, in seconds: one year.
 *
 * The policy protects only a visit that falls inside max-age of the previous one, so a short lifetime lapses
 * for the occasional visitor and hands their next first request back to plain HTTP, where it can be redirected
 * or read. One year is also the floor the browsers' preload list admits, the one route to protecting the very
 * first visit, and what OWASP recommends at least.
 */
const MINIMUM_TRANSPORT_SECURITY_SECONDS: number = 31_536_000

const TRANSPORT_SECURITY_HEADER: string = 'strict-transport-security'

const TRANSPORT_SECURITY_REPAIR: string =
  `send ${TRANSPORT_SECURITY_HEADER} from the production server with max-age=` +
  `${String(MINIMUM_TRANSPORT_SECURITY_SECONDS)} (one year) or more`

// RFC 6797 directives: `name[=value]` separated by semicolons, names compared without case, a value either a
// token or a quoted string. A directive that appears twice makes the whole header invalid, and a browser then
// ignores it, so two max-age directives are as good as none.
const maxAgesOf = (value: string): readonly string[] =>
  value.split(';').flatMap((part: string): readonly string[] => {
    const separator: number = part.indexOf('=')
    const name: string = (separator === -1 ? part : part.slice(0, separator)).trim().toLowerCase()
    return name === 'max-age' && separator !== -1
      ? [
          part
            .slice(separator + 1)
            .trim()
            .replaceAll('"', ''),
        ]
      : []
  })

const transportSecurityProblems = (value: string | undefined): readonly string[] => {
  if (value === undefined) {
    return [`${TRANSPORT_SECURITY_HEADER} must be declared; ${TRANSPORT_SECURITY_REPAIR}`]
  }
  const ages: readonly string[] = maxAgesOf(value)
  const age: string | undefined = ages.length === 1 ? ages[0] : undefined
  return age !== undefined && /^\d+$/u.test(age) && Number(age) >= MINIMUM_TRANSPORT_SECURITY_SECONDS
    ? []
    : [`${TRANSPORT_SECURITY_HEADER} is "${value}"; ${TRANSPORT_SECURITY_REPAIR}`]
}

// Playwright joins the Set-Cookie lines of one response with a line feed, the one header it does not join with
// a comma, because a cookie's Expires date carries a comma of its own.
const cookieLines = (value: string | undefined): readonly string[] =>
  (value ?? '')
    .split('\n')
    .map((line: string): string => line.trim())
    .filter((line: string): boolean => line.length > 0)

// RFC 6265 attributes follow the name-value pair, each `name[=value]`, the name compared without case. A browser
// ignores a value written after Secure, so `Secure=yes` is as secure as `Secure`.
const isSecureCookie = (line: string): boolean =>
  line
    .split(';')
    .slice(1)
    .some((attribute: string): boolean => attribute.split('=', 1).join('').trim().toLowerCase() === 'secure')

const cookieName = (line: string): string => {
  const pair: string = line.split(';', 1).join('')
  const separator: number = pair.indexOf('=')
  return (separator === -1 ? pair : pair.slice(0, separator)).trim()
}

const cookieProblems = (value: string | undefined): readonly string[] =>
  cookieLines(value)
    .filter((line: string): boolean => !isSecureCookie(line))
    .map(
      (line: string): string =>
        `cookie "${cookieName(line)}" is set without Secure, so a browser sends it over plain HTTP too; mark ` +
        'every cookie the production server sets Secure',
    )

const productionProblems = (headers: Readonly<Record<string, string>>): readonly string[] => [
  ...transportSecurityProblems(headers[TRANSPORT_SECURITY_HEADER]),
  ...cookieProblems(headers['set-cookie']),
]

/**
 * Judge the established baseline; this does not attempt complete CSP validation.
 * @param headers the response headers, named in lower case, with every Set-Cookie line of the response under
 *   `set-cookie` joined by line feeds, as Playwright's `APIResponse.headers()` and `Response.allHeaders()` give
 *   them.
 * @param servedBy which server answered: the production server a verification run starts is held to HTTPS
 *   through Strict-Transport-Security and Secure cookies as well, a development server is not.
 * @returns one problem per missing or defective decision, or nothing.
 */
export const securityHeaderProblems = (
  headers: Readonly<Record<string, string>>,
  servedBy: ServedBy,
): readonly string[] => {
  const policy: string = headers['content-security-policy'] ?? ''
  return [
    ...(headers['x-content-type-options'] === 'nosniff' ? [] : ['x-content-type-options must be nosniff']),
    ...(headers['referrer-policy'] === undefined ? ['referrer-policy must be declared'] : []),
    ...(headers['x-frame-options'] === undefined && directive(policy, 'frame-ancestors') === undefined
      ? ['declare a framing decision through x-frame-options or frame-ancestors']
      : []),
    ...scriptProblems(policy),
    ...(servedBy === 'production' ? productionProblems(headers) : []),
  ]
}
