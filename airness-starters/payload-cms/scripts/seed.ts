// Explicit, idempotent database seed (run via `pnpm seed`).
//
// Creates a known local admin so a fresh database is usable immediately: you can
// sign into /admin without the interactive create-first-user step. Re-running is
// a safe no-op.
//
// Hard-locked to a local database: if DATABASE_URL does not point at localhost
// the seed refuses to run, with no override. A known-password admin therefore
// cannot reach a remote/production database by any path. The password is
// required via SEED_ADMIN_PASSWORD (see .env.example), so no credential is ever
// embedded in source.
import 'dotenv/config'
import { getPayload, type Payload } from 'payload'
import { loadServerEnvironment } from '@/lib/environment'
import config from '@/payload.config'
import { DEVELOPMENT_ADMIN_IDENTITY, type DevelopmentAdmin, seedDevelopmentAdmin } from './development-admin'

const LOCAL_DATABASE_HOSTS: readonly string[] = ['127.0.0.1', 'localhost', '::1']

const adminPassword: string | undefined = process.env['SEED_ADMIN_PASSWORD']
// Importing the config above already refused a missing DATABASE_URL, so the validated value is read here.
const databaseHost: string = new URL(loadServerEnvironment().databaseUrl).hostname

// The one place this script reports a failed precondition. Setting the exit code is how a Node CLI
// reports a verdict while still flushing stdout; process.exit() would truncate the message just
// written above it.
const refuse = (reason: string): void => {
  console.error(`[seed] ${reason}`)
  process.exitCode = 1
}

if (!LOCAL_DATABASE_HOSTS.includes(databaseHost)) {
  refuse(`Refusing to seed a non-local database (host: ${databaseHost}). The seed supports localhost only.`)
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

  // A one-shot CLI must close Payload's open database pool; setting exitCode alone would hang the
  // process until the pool times out.
  // eslint-disable-next-line unicorn/no-process-exit -- an open DB pool would hang the process
  process.exit(0)
}
