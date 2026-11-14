// Validated server environment - the single, audited entry point for reading `process.env`.
//
// Every environment variable the application depends on is declared, validated, and narrowed here, then
// passed down as plain typed values.
// The validation core is pure: `loadServerEnvironment` reads the live `process.env` by default, and the
// tests hand it a source of their own, so it is tested without depending on the ambient environment.
//
// Design note: validation and construction happen in a SINGLE pass via a discriminated result, so there
// is no unreachable "re-narrow after validating" branch that no test could ever cover.

/** The validated, narrowed server environment passed down to the rest of the application. */
export interface ServerEnvironment {
  /** Signing secret for Payload tokens. Required; never has a default. */
  readonly payloadSecret: string
  /** Postgres connection string. Required; never has a default. */
  readonly databaseUrl: string
  /**
   * Public base URL of the site. Required in a production build, where it is the only CORS and CSRF origin;
   * outside one it defaults to the local development server.
   */
  readonly serverUrl: string
  /** Where uploaded files are stored. */
  readonly storage: StorageEnvironment
  /**
   * How many reverse proxies in front of the server append the address they received a request from to
   * `X-Forwarded-For`. The client address is the entry this many places from the end, the one the outermost trusted
   * proxy wrote. Defaults to one.
   */
  readonly trustedProxyCount: number
}

/** The S3-compatible bucket uploads are stored in. */
interface StorageEnvironment {
  /** The S3 endpoint URL. Required. */
  readonly endpoint: string
  /** The bucket uploads are written to. Required. */
  readonly bucket: string
  /** The region requests are signed for. Defaults to us-east-1, which RustFS accepts. */
  readonly region: string
  /** The access key ID. Required; never has a default. */
  readonly accessKeyId: string
  /** The secret access key. Required; never has a default. */
  readonly secretAccessKey: string
}

// `NODE_ENV` is the framework's variable rather than this project's, so it is read with a dot: the bracketed
// reads below are the project's own configuration, each documented in the example file.
type EnvironmentSource = Readonly<Record<string, string | undefined>> & { readonly NODE_ENV?: string | undefined }

type FieldResult = { readonly value: string } | { readonly issue: string }

type EnvironmentResult =
  | { readonly ok: true; readonly environment: ServerEnvironment }
  | { readonly ok: false; readonly issues: readonly string[] }

const DEFAULT_SERVER_URL: string = 'http://localhost:3000'
const DEFAULT_S3_REGION: string = 'us-east-1'
const DEFAULT_TRUSTED_PROXY_COUNT: number = 1

const isNonEmpty = (value: string | undefined): value is string => value !== undefined && value.trim().length > 0

const hasIssue = (result: FieldResult): result is { readonly issue: string } => 'issue' in result

// A required variable: present and non-empty, or it contributes an issue.
const required = (value: string | undefined, name: string): FieldResult =>
  isNonEmpty(value) ? { value } : { issue: `${name} is required and must be non-empty.` }

// A required URL variable: present, non-empty and parseable, or it contributes an issue.
const requiredUrl = (value: string | undefined, name: string): FieldResult => {
  const present: FieldResult = required(value, name)
  if (hasIssue(present)) {
    return present
  }
  return URL.canParse(present.value) ? present : { issue: `${name} must be a valid URL.` }
}

// An optional URL variable: falls back to the default when unset, but a SET value must parse.
const optionalUrl = (value: string | undefined, name: string, fallback: string): FieldResult => {
  if (!isNonEmpty(value)) {
    return { value: fallback }
  }
  return URL.canParse(value) ? { value } : { issue: `${name} must be a valid URL when set.` }
}

type CountResult = { readonly count: number } | { readonly issue: string }

// An optional count of at least one: falls back to the default when unset, but a SET value must be a whole number.
const optionalPositiveCount = (value: string | undefined, name: string, fallback: number): CountResult => {
  if (!isNonEmpty(value)) {
    return { count: fallback }
  }
  const count: number = Number(value)
  return Number.isSafeInteger(count) && count >= 1
    ? { count }
    : { issue: `${name} must be a whole number of at least 1 when set.` }
}

type StorageResult =
  | { readonly ok: true; readonly storage: StorageEnvironment }
  | { readonly ok: false; readonly issues: readonly string[] }

const issuesOf = (results: readonly FieldResult[]): readonly string[] =>
  results.filter(hasIssue).map((result: { readonly issue: string }): string => result.issue)

// The upload storage, validated the same way: every required variable present, the endpoint a URL.
const readStorageEnvironment = (source: EnvironmentSource): StorageResult => {
  const endpoint: FieldResult = requiredUrl(source['S3_ENDPOINT'], 'S3_ENDPOINT')
  const bucket: FieldResult = required(source['S3_BUCKET'], 'S3_BUCKET')
  const accessKey: FieldResult = required(source['S3_ACCESS_KEY_ID'], 'S3_ACCESS_KEY_ID')
  const secretKey: FieldResult = required(source['S3_SECRET_ACCESS_KEY'], 'S3_SECRET_ACCESS_KEY')
  if (hasIssue(endpoint) || hasIssue(bucket) || hasIssue(accessKey) || hasIssue(secretKey)) {
    return { ok: false, issues: issuesOf([endpoint, bucket, accessKey, secretKey]) }
  }
  const region: string | undefined = source['S3_REGION']
  return {
    ok: true,
    storage: {
      endpoint: endpoint.value,
      bucket: bucket.value,
      region: isNonEmpty(region) ? region : DEFAULT_S3_REGION,
      accessKeyId: accessKey.value,
      secretAccessKey: secretKey.value,
    },
  }
}

// The public origin. A production build has to be told it: falling back to the development address there would
// make localhost the only CORS and CSRF origin, and Payload then ignores the session cookie of every admin request
// from the real origin, so each save fails as unauthenticated with nothing saying why.
const readServerUrl = (source: EnvironmentSource): FieldResult =>
  source.NODE_ENV === 'production'
    ? requiredUrl(source['NEXT_PUBLIC_SERVER_URL'], 'NEXT_PUBLIC_SERVER_URL')
    : optionalUrl(source['NEXT_PUBLIC_SERVER_URL'], 'NEXT_PUBLIC_SERVER_URL', DEFAULT_SERVER_URL)

// Validate and construct in one pass. The `||` guard narrows every field to its success variant for
// the type-checker; the parallel `issues` list carries the human-readable problems.
const readServerEnvironment = (source: EnvironmentSource): EnvironmentResult => {
  const secret: FieldResult = required(source['PAYLOAD_SECRET'], 'PAYLOAD_SECRET')
  const database: FieldResult = required(source['DATABASE_URL'], 'DATABASE_URL')
  const server: FieldResult = readServerUrl(source)
  const storage: StorageResult = readStorageEnvironment(source)
  const proxies: CountResult = optionalPositiveCount(
    source['TRUSTED_PROXY_COUNT'],
    'TRUSTED_PROXY_COUNT',
    DEFAULT_TRUSTED_PROXY_COUNT,
  )

  if (hasIssue(secret) || hasIssue(database) || hasIssue(server) || !storage.ok || 'issue' in proxies) {
    const issues: readonly string[] = [
      ...issuesOf([secret, database, server]),
      ...(storage.ok ? [] : storage.issues),
      ...('issue' in proxies ? [proxies.issue] : []),
    ]
    return { ok: false, issues }
  }

  return {
    ok: true,
    environment: {
      payloadSecret: secret.value,
      databaseUrl: database.value,
      serverUrl: server.value,
      storage: storage.storage,
      trustedProxyCount: proxies.count,
    },
  }
}

/**
 * Read, validate, and narrow the server environment, throwing an aggregated error that lists EVERY
 * problem at once rather than failing on the first.
 * @param source the variables to read, `process.env` unless a caller such as a test names others.
 * @returns the validated environment.
 */
export const loadServerEnvironment = (source: EnvironmentSource = process.env): ServerEnvironment => {
  const result: EnvironmentResult = readServerEnvironment(source)
  if (!result.ok) {
    const detail: string = result.issues.map((issue: string): string => `  - ${issue}`).join('\n')
    throw new Error(`Invalid environment configuration:\n${detail}`)
  }
  return result.environment
}

/**
 * Whether the application is running outside a production build.
 *
 * Separate from {@link ServerEnvironment} on purpose. The proxy (`src/proxy.ts`), the session cookie and the
 * front page read it, and the proxy runs on every request: routing it through {@link loadServerEnvironment}
 * would make a missing `PAYLOAD_SECRET` fail every request rather than the one boot that should have caught
 * it. `NODE_ENV` is not part of the validated contract (see `EnvironmentSource`).
 * @returns true unless NODE_ENV names a production build.
 */
export const isDevelopment = (): boolean => process.env.NODE_ENV !== 'production'
