// Seeds the demo content alone, without any account (run via `pnpm run seed:content`).
//
// The verification wrapper, `scripts/with-services.ts`, runs it against every disposable database it starts, so the
// browser sweep crawls a site with posts, categories and covers rather than an empty one, and reaches every page
// template. Locked to a local database like `pnpm seed`.
import 'dotenv/config'
import { getPayload, type Payload } from 'payload'
import config from '@/payload.config'
import { type SeedReport, seedDemoContent } from './seed/demo-content'
import { databaseHost, isLocalDatabase } from './seed/local-database'

const host: string = databaseHost()

if (isLocalDatabase(host)) {
  const payload: Payload = await getPayload({ config })
  const content: SeedReport = await seedDemoContent(payload)
  console.info(`[seed] Created ${String(content.categories)} categories and ${String(content.posts)} posts.`)
  // A one-shot CLI must close Payload's open database pool; setting exitCode alone would hang the
  // process until the pool times out.
  // eslint-disable-next-line unicorn/no-process-exit -- an open DB pool would hang the process
  process.exit(0)
}
console.error(`[seed] Refusing to seed a non-local database (host: ${host}). The seed supports localhost only.`)
process.exitCode = 1
