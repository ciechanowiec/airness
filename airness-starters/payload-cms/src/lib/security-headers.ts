// The security headers the application answers with, as pure values.
//
// This lives apart from the proxy (`src/proxy.ts`) because the policy differs between a development server and
// a production build, and a suite that runs in one of those can only ever take one side of that branch.
// Passing the mode in as an argument is what lets both be tested; reading it inside would leave the
// production policy - the one that actually ships - permanently unexercised, and no substitute may be
// installed to pretend otherwise.

/** The value a policy names when it means "nothing at all". */
const NONE: string = "'none'"

/** The value a policy names when it means "this origin only". */
const SELF: string = "'self'"

// `'unsafe-eval'` is the price of the development server: it evaluates modules with `eval` while hot
// reloading, and reloads over a websocket that `connect-src` has to admit. Neither is granted to a
// production build, which is the whole reason the mode is a parameter here.
const scriptSources = (nonce: string, isDevelopment: boolean): string =>
  [SELF, `'nonce-${nonce}'`, "'strict-dynamic'", ...(isDevelopment ? ["'unsafe-eval'"] : [])].join(' ')

const connectSources = (isDevelopment: boolean): string => [SELF, ...(isDevelopment ? ['ws:', 'wss:'] : [])].join(' ')

/**
 * Build the Content-Security-Policy for one response.
 *
 * `style-src` keeps `'unsafe-inline'`: React and Tailwind both emit inline style attributes, and a
 * style sheet cannot execute. The directive that stops injection is `script-src`, and it admits script
 * by nonce rather than by permitting inline script at all.
 * @param nonce - the per-response nonce the renderer stamps onto the scripts it injects.
 * @param isDevelopment - whether this is a development server rather than a production build.
 * @returns the policy, as the header value.
 */
export const contentSecurityPolicy = (nonce: string, isDevelopment: boolean): string =>
  [
    `default-src ${SELF}`,
    `script-src ${scriptSources(nonce, isDevelopment)}`,
    `style-src ${SELF} 'unsafe-inline'`,
    `img-src ${SELF} blob: data:`,
    `font-src ${SELF}`,
    `connect-src ${connectSources(isDevelopment)}`,
    `object-src ${NONE}`,
    `base-uri ${SELF}`,
    `form-action ${SELF}`,
    `frame-ancestors ${NONE}`,
  ].join('; ')

// Two years, the duration browsers' preload lists ask for, and every subdomain with it.
const STRICT_TRANSPORT_SECURITY: string = 'max-age=63072000; includeSubDomains'

const ALWAYS: Readonly<Record<string, string>> = {
  // Stops a browser re-interpreting a response as a type this application never declared, which is what
  // makes an uploaded file executable as something other than what it was stored as.
  'x-content-type-options': 'nosniff',
  // Keeps the full URL of a page here from reaching every origin it links to.
  'referrer-policy': 'strict-origin-when-cross-origin',
  // The older spelling of the frame-ancestors decision, for a browser that reads only this one.
  'x-frame-options': 'DENY',
}

/**
 * The headers that carry no per-request value, so they are the same on every response of one mode.
 *
 * A production build adds Strict-Transport-Security, which makes a browser reach the site over HTTPS only, so no
 * request of a later visit travels over plain HTTP where it could be read or redirected. The development server
 * leaves it out: a browser keeps the decision for the whole duration, and it would hold `localhost` to HTTPS for
 * every project a developer serves there.
 * @param isDevelopment - whether this is a development server rather than a production build.
 * @returns the headers, by name.
 */
export const staticSecurityHeaders = (isDevelopment: boolean): Readonly<Record<string, string>> =>
  isDevelopment ? ALWAYS : { ...ALWAYS, 'strict-transport-security': STRICT_TRANSPORT_SECURITY }
