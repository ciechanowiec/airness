// Security decisions over the configuration Payload actually resolves, including plugin contributions.
import { isArray, isRecord, readKey } from '@airness/node/json-shapes'
import { parsePayloadReport } from './payload-defaults.js'

/** Authentication values observed after Payload has applied its defaults and plugins. */
export interface ResolvedAuth {
  readonly slug: string
  readonly local: boolean
  readonly maxLoginAttempts: unknown
  readonly lockTime: unknown
  readonly tokenExpiration: unknown
  /** `auth.cookies.secure`, which Payload resolves to false unless the project decides it. */
  readonly cookieSecure: unknown
  /** `auth.cookies.sameSite`, which forces the cookie Secure when it is `None`. */
  readonly cookieSameSite: unknown
}

/** Only the state is reported: an auto-login object can contain credentials. */
export interface PayloadSecurityReport {
  readonly authentication: readonly ResolvedAuth[]
  readonly autoLogin: 'disabled' | 'enabled' | 'invalid'
}

const autoLoginState = (value: unknown): PayloadSecurityReport['autoLogin'] => {
  if (value === undefined || value === false) {
    return 'disabled'
  }
  return isRecord(value) ? 'enabled' : 'invalid'
}

// Null rather than undefined for an absent value, so the report keeps the key and a missing setting is judged.
const observed = (owner: unknown, key: string): unknown => readKey(owner, key) ?? null

const hasLocalStrategy = (auth: unknown): boolean => {
  const disabled: unknown = readKey(auth, 'disableLocalStrategy')
  if (disabled === undefined || disabled === false) {
    return true
  }
  if (disabled === true || isRecord(disabled)) {
    return false
  }
  throw new TypeError('resolved auth.disableLocalStrategy is not a supported strategy setting')
}

/** Read security-relevant values without invoking access functions or retaining credentials. */
export const payloadSecurityOf = (config: unknown): PayloadSecurityReport => {
  const collections: unknown = readKey(config, 'collections')
  if (!isArray(collections)) {
    throw new TypeError('the resolved Payload configuration has no collections array')
  }
  return {
    autoLogin: autoLoginState(readKey(readKey(config, 'admin'), 'autoLogin')),
    authentication: collections.flatMap((collection: unknown): readonly ResolvedAuth[] => {
      const auth: unknown = readKey(collection, 'auth')
      if (auth === undefined || auth === false) {
        return []
      }
      const slug: unknown = readKey(collection, 'slug')
      if (typeof slug !== 'string' || !isRecord(auth)) {
        throw new TypeError('an authentication collection has no resolved slug or auth object')
      }
      const cookies: unknown = readKey(auth, 'cookies')
      return [
        {
          slug,
          local: hasLocalStrategy(auth),
          maxLoginAttempts: observed(auth, 'maxLoginAttempts'),
          lockTime: observed(auth, 'lockTime'),
          tokenExpiration: observed(auth, 'tokenExpiration'),
          cookieSecure: observed(cookies, 'secure'),
          cookieSameSite: observed(cookies, 'sameSite'),
        },
      ]
    }),
  }
}

const isResolvedAuth = (value: unknown): value is ResolvedAuth =>
  isRecord(value) &&
  typeof value['slug'] === 'string' &&
  typeof value['local'] === 'boolean' &&
  ['maxLoginAttempts', 'lockTime', 'tokenExpiration', 'cookieSecure', 'cookieSameSite'].every((key: string): boolean =>
    Object.hasOwn(value, key),
  )

/** Missing security evidence is a probe failure, even if the access portion was readable. */
export const parsePayloadSecurityReport = (text: string): PayloadSecurityReport | undefined => {
  const report: unknown = parsePayloadReport(text)
  const authentication: unknown = readKey(report, 'authentication')
  if (!(isArray(authentication) && authentication.every(isResolvedAuth))) {
    return undefined
  }
  const autoLogin: unknown = readKey(report, 'autoLogin')
  return isAutoLoginState(autoLogin) ? { authentication, autoLogin } : undefined
}

const isPositive = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value) && value > 0

const isAutoLoginState = (value: unknown): value is PayloadSecurityReport['autoLogin'] =>
  typeof value === 'string' && ['disabled', 'enabled', 'invalid'].includes(value)

const UNITS: Readonly<Record<string, string>> = { lockTime: ' (milliseconds)', tokenExpiration: ' (seconds)' }

const authFindings = (auth: ResolvedAuth): readonly string[] => {
  if (!auth.local) {
    return []
  }
  return ['maxLoginAttempts', 'lockTime', 'tokenExpiration'].flatMap((key: string): readonly string[] => {
    const value: unknown = readKey(auth, key)
    const isValid: boolean = isPositive(value) && (key !== 'maxLoginAttempts' || Number.isSafeInteger(value))
    const requirement: string = key === 'maxLoginAttempts' ? 'a positive integer' : 'a finite positive number'
    const unit: string = UNITS[key] ?? ''
    return isValid
      ? []
      : [`collection "${auth.slug}" resolves auth.${key}${unit} to ${JSON.stringify(value)}; set ${requirement}`]
  })
}

// The session cookie of an auth collection, which Payload sets on login and on every refresh, whatever strategy
// signed the user in. Payload resolves `auth.cookies.secure` to false unless the project decides it, so a
// browser sends the session token over plain HTTP too: on a link to `http://`, on a first request typed without
// a scheme, before any redirect to HTTPS can answer. Payload marks the cookie Secure either when `secure` is
// true or when `sameSite` is `None`, which browsers accept only on a Secure cookie, and this reads the two as
// Payload does. The probe evaluates the configuration where NODE_ENV is production, so a project that decides
// `secure` from it, through its validated environment module, is judged by what it deploys.
const cookieFindings = (auth: ResolvedAuth): readonly string[] =>
  auth.cookieSecure === true || auth.cookieSameSite === 'None'
    ? []
    : [
        `collection "${auth.slug}" resolves auth.cookies.secure to ${JSON.stringify(auth.cookieSecure)} in ` +
          'production, so its session cookie is sent over plain HTTP too; set auth.cookies.secure to true when ' +
          'NODE_ENV is production',
      ]

/**
 * Enforce native authentication limits and Secure session cookies, and disable the development login feature,
 * in production.
 */
export const findPayloadSecurityViolations = (report: PayloadSecurityReport): readonly string[] => [
  ...report.authentication.flatMap((auth: ResolvedAuth): readonly string[] => [
    ...authFindings(auth),
    ...cookieFindings(auth),
  ]),
  ...(report.autoLogin === 'disabled'
    ? []
    : ['production admin.autoLogin must be absent or false; disable auto-login and credential prefill in production']),
]
