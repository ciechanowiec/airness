// Starts the database and the application together, as compose.yaml describes them, on a loopback port free on
// this machine, so another checkout of the project, or another project, runs beside it.
//
// The port is chosen here rather than written into compose.yaml, which publishes the application on APP_PORT. The
// script prints the address before compose starts, and compose then streams the logs until it is stopped.
import { type SpawnSyncReturns, spawnSync } from 'node:child_process'
import { createServer, type Server } from 'node:net'

const LOOPBACK: string = '127.0.0.1'

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

// A port the system has free right now: bound and released again, so compose can take it.
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

const port: number = await freePort()
process.stdout.write(`The application answers at http://localhost:${String(port)} once it has started.\n`)
const [program = 'docker', ...prefix]: readonly string[] = composeCommand()
const compose: SpawnSyncReturns<Buffer> = spawnSync(program, [...prefix, 'up', '--build'], {
  env: { ...process.env, APP_PORT: String(port) },
  stdio: 'inherit',
})
process.exitCode = compose.status ?? 1
