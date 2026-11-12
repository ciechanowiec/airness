// The one statement at a time the fixture sends, over PostgreSQL's wire protocol under the trust authentication its
// database runs with, so the image needs no driver and its build reads no registry.
import { connect, type Socket } from 'node:net'

const PROTOCOL_VERSION: number = 196_608
const HEADER_BYTES: number = 5

const cstring = (text: string): Buffer => Buffer.from(`${text}\0`)

const message = (type: string, body: Buffer): Buffer => {
  const length: Buffer = Buffer.alloc(4)
  length.writeInt32BE(body.length + 4)
  return Buffer.concat([Buffer.from(type), length, body])
}

const startup = (url: URL): Buffer => {
  const body: Buffer = Buffer.concat([
    cstring('user'),
    cstring(decodeURIComponent(url.username)),
    cstring('database'),
    cstring(url.pathname.slice(1)),
    Buffer.from([0]),
  ])
  const header: Buffer = Buffer.alloc(8)
  header.writeInt32BE(body.length + 8, 0)
  header.writeInt32BE(PROTOCOL_VERSION, 4)
  return Buffer.concat([header, body])
}

// The human-readable message of an ErrorResponse, its field M.
const errorOf = (body: Buffer): string =>
  body
    .toString('utf8')
    .split('\0')
    .find((field: string): boolean => field.startsWith('M'))
    ?.slice(1) ?? 'the database reported an error'

/**
 * Run one statement against the database a URL names, and fail with the database's own message when it fails.
 * @param databaseUrl the database, as postgres://user@host:port/database.
 * @param sql the statement.
 */
export const execute = async (databaseUrl: string, sql: string): Promise<void> => {
  const url: URL = new URL(databaseUrl)
  const socket: Socket = connect(Number(url.port === '' ? '5432' : url.port), url.hostname)
  await new Promise<void>((resolve, reject): void => {
    let buffered: Buffer = Buffer.alloc(0)
    let sent: boolean = false
    let failure: string | undefined
    socket.once('connect', (): void => {
      socket.write(startup(url))
    })
    socket.once('error', reject)
    socket.on('data', (chunk: Buffer): void => {
      buffered = Buffer.concat([buffered, chunk])
      while (buffered.length >= HEADER_BYTES) {
        const type: string = String.fromCodePoint(buffered[0] ?? 0)
        const length: number = buffered.readInt32BE(1)
        if (buffered.length < length + 1) {
          return
        }
        const body: Buffer = buffered.subarray(HEADER_BYTES, length + 1)
        buffered = buffered.subarray(length + 1)
        if (type === 'R' && body.readInt32BE(0) !== 0) {
          socket.destroy()
          reject(new Error('the database asked for a credential, but the fixture runs it under trust'))
          return
        }
        if (type === 'E') {
          failure = errorOf(body)
        }
        if (type === 'Z' && !sent && failure === undefined) {
          sent = true
          socket.write(message('Q', cstring(sql)))
        } else if (type === 'Z') {
          socket.end(message('X', Buffer.alloc(0)))
          if (failure === undefined) {
            resolve()
          } else {
            reject(new Error(failure))
          }
        }
      }
    })
  })
}
