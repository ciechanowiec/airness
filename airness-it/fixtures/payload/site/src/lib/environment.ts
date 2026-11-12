// The one module that reads the environment: every other module consumes the typed values it returns.

/** The validated environment the configuration boots with. */
export interface Environment {
  readonly payloadSecret: string
  readonly databaseUrl: string
}

/** Raw environment variables, as the process holds them. */
type EnvironmentSource = Readonly<Record<string, string | undefined>>

const required = (source: EnvironmentSource, name: string): string => {
  const value: string | undefined = source[name]
  if (value === undefined || value.trim().length === 0) {
    throw new Error(`${name} is required and was not set`)
  }
  return value
}

/**
 * Read and validate the environment from a plain record.
 * @param source the raw variables, normally the process environment.
 * @returns the narrowed values.
 */
const parseEnvironment = (source: EnvironmentSource): Environment => ({
  payloadSecret: required(source, 'PAYLOAD_SECRET'),
  databaseUrl: required(source, 'DATABASE_URL'),
})

// The variables the process carries.
const processEnvironment = (): EnvironmentSource => ({
  PAYLOAD_SECRET: process.env['PAYLOAD_SECRET'],
  DATABASE_URL: process.env['DATABASE_URL'],
})

/**
 * Read and validate the environment, failing fast on a missing variable.
 * @param source the raw variables, the process environment unless the caller passes a record of its own.
 * @returns the narrowed values.
 */
export const loadEnvironment = (source: EnvironmentSource = processEnvironment()): Environment =>
  parseEnvironment(source)

/**
 * Whether the process runs as the deployed application, which is reached over HTTPS alone. `NODE_ENV` is the
 * runtime's own variable, read with a dot, so no example file claims to document it.
 * @returns true in production.
 */
export const isProduction = (): boolean => process.env.NODE_ENV === 'production'
