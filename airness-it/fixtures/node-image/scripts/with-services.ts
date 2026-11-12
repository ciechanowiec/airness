// Runs one command against a PostgreSQL of its own, migrated first, as a governed project's wrapper does. The
// database trusts every connection, so the image's server reaches it without a driver that would handle a credential.
import { execFileSync } from 'node:child_process'
import { withServices } from '@airness/node/services'

process.exitCode = await withServices({
  project: 'fixture-image',
  containers: [
    {
      key: 'db',
      image: 'postgres:18-alpine@sha256:10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5',
      environment: { POSTGRES_DB: 'fixture', POSTGRES_USER: 'fixture', POSTGRES_HOST_AUTH_METHOD: 'trust' },
      ports: ['5432/tcp'],
      ready: ['pg_isready', '--host=127.0.0.1', '--username=fixture', '--dbname=fixture'],
    },
  ],
  environment: (services) => ({
    DATABASE_URL: `postgres://fixture@127.0.0.1:${String(services.port('db', '5432/tcp'))}/fixture`,
  }),
  prepare: (_services, environment) => {
    execFileSync(process.execPath, ['scripts/migrate.ts'], { env: { ...process.env, ...environment }, stdio: 'inherit' })
  },
})
