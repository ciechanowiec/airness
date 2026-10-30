// The one module that reads the environment: every other module consumes the typed values it returns.

/** The validated environment the configuration boots with. */
export interface Environment {
  readonly payloadSecret: string
  readonly databaseUrl: string
}

/** Raw environment variables, as the process holds them. */
export type EnvironmentSource = Readonly<Record<string, string | undefined>>

const required = (source: EnvironmentSource, name: string): string => {
  const value: string | undefined = source[name]
  if (value === undefined || value.trim().length === 0) {
    throw new Error(`${name} is required and was not set`)
  }
  return value
}

/**
 * Read and validate the environment from a plain record, which keeps the rule testable.
 * @param source the raw variables, normally the process environment.
 * @returns the narrowed values.
 */
export const parseEnvironment = (source: EnvironmentSource): Environment => ({
  payloadSecret: required(source, 'PAYLOAD_SECRET'),
  databaseUrl: required(source, 'DATABASE_URL'),
})

/**
 * Read and validate the process environment, failing fast on a missing variable.
 * @returns the narrowed values.
 */
export const loadEnvironment = (): Environment =>
  parseEnvironment({
    PAYLOAD_SECRET: process.env['PAYLOAD_SECRET'],
    DATABASE_URL: process.env['DATABASE_URL'],
  })
