// Runs the application on this machine against the services compose.yaml describes, each on a loopback port free
// here, so another checkout of the project, or another project, runs beside it.
//
// Without arguments it starts the database, the object storage and the application together through compose, as
// a deployment would run the image, and streams their logs until it is stopped. With a command, such as `next dev`
// or the seed, it starts the two services alone, applies the pending migrations and runs the command on the host
// against them. Either way the ports are chosen here, rather than written into compose.yaml, and the application
// is told the origin it is served on, which it accepts authenticated requests from alone.
import { type SpawnSyncReturns, spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { createServer, type Server } from 'node:net'

const LOOPBACK: string = '127.0.0.1'
const ENVIRONMENT_FILE: string = '.env'
const FIRST_ARGUMENT: number = 2

// Compose ships as a docker subcommand and as a standalone binary, and which one a machine has is no property of the
// project, so the subcommand is tried first.
const COMPOSE_COMMANDS: readonly (readonly string[])[] = [['docker', 'compose'], ['docker-compose']]

const composeCommand = (): readonly string[] => {
  const found: readonly string[] | undefined = COMPOSE_COMMANDS.find(
    ([program, ...prefix]: readonly string[]): boolean =>
      program !== undefined && spawnSync(program, [...prefix, 'version'], { stdio: 'ignore' }).status === 0,
  )
  if (found === undefined) {
    throw new Error('Docker Compose is required: neither docker compose nor docker-compose answers')
  }
  return found
}

// A port the system has free right now: bound and released again, so compose or the server can take it.
const freePort = async (): Promise<number> =>
  new Promise<number>((resolve, reject) => {
    const server: Server = createServer()
    server.once('error', reject)
    server.listen(0, LOOPBACK, () => {
      const address: ReturnType<Server['address']> = server.address()
      const port: number = typeof address === 'object' && address !== null ? address.port : 0
      server.close(() => {
        resolve(port)
      })
    })
  })

// The value .env gives a variable, which compose reads by itself but a command on the host does not.
const configured = (name: string): string => {
  const value: string | undefined = process.env[name]
  if (value === undefined || value === '') {
    throw new Error(`${name} is empty: copy .env.example to .env and choose the values it leaves empty`)
  }
  return value
}

const run = (command: string, arguments_: readonly string[], environment: NodeJS.ProcessEnv): number => {
  const result: SpawnSyncReturns<Buffer> = spawnSync(command, arguments_, { env: environment, stdio: 'inherit' })
  return result.status ?? 1
}

if (existsSync(ENVIRONMENT_FILE)) {
  process.loadEnvFile(ENVIRONMENT_FILE)
}
const [appPort, databasePort, storagePort]: readonly number[] = [await freePort(), await freePort(), await freePort()]
const origin: string = `http://localhost:${String(appPort)}`
const ports: NodeJS.ProcessEnv = {
  ...process.env,
  APP_PORT: String(appPort),
  DB_PORT: String(databasePort),
  S3_PORT: String(storagePort),
}
// The arguments after the interpreter and this script.
const command: readonly string[] = process.argv.slice(FIRST_ARGUMENT)
const [compose = 'docker', ...composePrefix]: readonly string[] = composeCommand()
const [program, ...rest]: readonly string[] = command

if (program === undefined) {
  process.stdout.write(`Once it has started, the application answers at ${origin}/admin\n`)
  process.exitCode = run(compose, [...composePrefix, 'up', '--build'], ports)
} else {
  const services: number = run(compose, [...composePrefix, 'up', '--detach', '--wait', 'db', 'objects'], ports)
  const user: string = encodeURIComponent(configured('POSTGRES_USER'))
  const password: string = encodeURIComponent(configured('POSTGRES_PASSWORD'))
  const database: string = encodeURIComponent(configured('POSTGRES_DB'))
  const environment: NodeJS.ProcessEnv = {
    ...ports,
    DATABASE_URL: `postgres://${user}:${password}@localhost:${String(databasePort)}/${database}`,
    S3_ENDPOINT: `http://localhost:${String(storagePort)}`,
    NEXT_PUBLIC_SERVER_URL: origin,
    NEXT_TELEMETRY_DISABLED: '1',
    PORT: String(appPort),
  }
  process.stdout.write(`Once the command serves it, the application answers at ${origin}/admin\n`)
  const migrated: number = services === 0 ? run('pnpm', ['run', 'migrate'], environment) : services
  process.exitCode = migrated === 0 ? run(program, rest, environment) : migrated
}
