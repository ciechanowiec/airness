// Answers 200 while the database it is given holds its table, on the port and origin it is given, and 500 when the
// database cannot answer, as a server whose schema is missing does.
import { createServer, type IncomingMessage, type ServerResponse } from 'node:http'
import { execute } from './database.ts'
import { migrate } from './migrate.ts'

const origin: string | undefined = process.env['APP_ORIGIN']
const databaseUrl: string | undefined = process.env['DATABASE_URL']
if (origin === undefined || databaseUrl === undefined) {
  console.error('APP_ORIGIN and DATABASE_URL are required')
  process.exit(1)
}
await migrate(databaseUrl)

createServer((_request: IncomingMessage, response: ServerResponse): void => {
  execute(databaseUrl, 'SELECT count(*) FROM greeting').then(
    (): void => {
      response.writeHead(200, { 'content-type': 'text/plain' })
      response.end(`greetings served on ${origin}\n`)
    },
    (error: unknown): void => {
      console.error(error instanceof Error ? error.message : String(error))
      response.writeHead(500)
      response.end()
    },
  )
}).listen(Number(process.env['PORT'] ?? '3000'), '0.0.0.0')
