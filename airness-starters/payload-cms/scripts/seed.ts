// Explicit, idempotent database seed (run via `pnpm seed`).
//
// Creates a known local admin so a fresh database is usable immediately: you can
// sign into /admin without the interactive create-first-user step. Then fills the
// site with the demo content of `scripts/seed/posts.ts`. Re-running is a safe no-op.
//
// Hard-locked to a local database: if DATABASE_URL does not point at localhost
// the seed refuses to run, with no override. A known-password admin therefore
// cannot reach a remote/production database by any path. The password is
// required via SEED_ADMIN_PASSWORD (see .env.example), so no credential is ever
// embedded in source.
import 'dotenv/config'
import { getPayload, type Payload } from 'payload'
import config from '@/payload.config'
import { DEVELOPMENT_ADMIN_IDENTITY, type DevelopmentAdmin, seedDevelopmentAdmin } from './development-admin'
import { type SeedReport, seedDemoContent } from './seed/demo-content'
import { databaseHost, isLocalDatabase } from './seed/local-database'

const adminPassword: string | undefined = process.env['SEED_ADMIN_PASSWORD']
// Importing the config above already refused a missing DATABASE_URL, so the validated value is read here.
const host: string = databaseHost()

// The one place this script reports a failed precondition. Setting the exit code is how a Node CLI
// reports a verdict while still flushing stdout; process.exit() would truncate the message just
// written above it.
const refuse = (reason: string): void => {
  console.error(`[seed] ${reason}`)
  process.exitCode = 1
}

if (!isLocalDatabase(host)) {
  refuse(`Refusing to seed a non-local database (host: ${host}). The seed supports localhost only.`)
} else if (adminPassword === undefined || adminPassword.length === 0) {
  refuse('SEED_ADMIN_PASSWORD is required. Set it in .env (copy from .env.example).')
} else {
  const admin: DevelopmentAdmin = {
    email: process.env['SEED_ADMIN_EMAIL'] ?? DEVELOPMENT_ADMIN_IDENTITY.email,
    name: process.env['SEED_ADMIN_NAME'] ?? DEVELOPMENT_ADMIN_IDENTITY.name,
    password: adminPassword,
    roles: DEVELOPMENT_ADMIN_IDENTITY.roles,
  }

  const payload: Payload = await getPayload({ config })
  const result: 'created' | 'exists' = await seedDevelopmentAdmin(payload, admin)
  console.info(
    result === 'created'
      ? `[seed] Created admin ${admin.email}.`
      : `[seed] Admin ${admin.email} already exists; nothing to do.`,
  )
  const content: SeedReport = await seedDemoContent(payload)
  console.info(`[seed] Created ${String(content.categories)} categories and ${String(content.posts)} posts.`)

  // A one-shot CLI must close Payload's open database pool; setting exitCode alone would hang the
  // process until the pool times out.
  // eslint-disable-next-line unicorn/no-process-exit -- an open DB pool would hang the process
  process.exit(0)
}
