import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { postgresAdapter } from '@payloadcms/db-postgres'
import { lexicalEditor } from '@payloadcms/richtext-lexical'
import { s3Storage } from '@payloadcms/storage-s3'
import { buildConfig } from 'payload'
import sharp from 'sharp'
import { AuditEvents } from './collections/AuditEvents'
import { Media } from './collections/Media'
import { Users } from './collections/Users'
import { auditAuthenticationFailure } from './hooks/audit'
import { concealLockedAccount } from './hooks/login-concealment'
import { loadServerEnvironment, type ServerEnvironment } from './lib/environment'
import { migrations } from './migrations'

const filename: string = fileURLToPath(import.meta.url)
const dirname: string = path.dirname(filename)

// Fail fast: never boot with a missing signing secret, database connection, or upload storage.
const environment: ServerEnvironment = loadServerEnvironment()

export default buildConfig({
  admin: {
    user: Users.slug,
    importMap: {
      baseDir: path.resolve(dirname),
    },
  },
  collections: [Users, Media, AuditEvents],
  editor: lexicalEditor(),
  // Root hooks rather than collection ones, because a GraphQL request reaches only these. The trail records the
  // refusal as it happened; the concealment then changes only what the caller is told.
  hooks: {
    afterError: [auditAuthenticationFailure, concealLockedAccount],
  },
  secret: environment.payloadSecret,
  // Explicit allowlist instead of the permissive default.
  cors: [environment.serverUrl],
  csrf: [environment.serverUrl],
  // Guard against over-fetching / deeply nested population.
  defaultDepth: 1,
  maxDepth: 3,
  typescript: {
    outputFile: path.resolve(dirname, 'payload-types.ts'),
  },
  db: postgresAdapter({
    pool: {
      connectionString: environment.databaseUrl,
    },
    // The schema comes from the committed migrations in src/migrations, applied by `pnpm run migrate`,
    // rather than from a boot-time push nothing in the repository records.
    push: false,
    // A production server applies the pending committed migrations when Payload starts, so a fresh database gets
    // its schema without a step the image cannot run: the standalone image carries neither the Payload CLI nor
    // src/migrations. A development server and `pnpm run migrate` keep applying them through the CLI.
    prodMigrations: migrations,
  }),
  sharp,
  // The plugin turns local storage off for the collections it handles. Path-style addressing, because
  // RustFS serves buckets under the endpoint's path rather than as subdomains.
  plugins: [
    s3Storage({
      collections: { media: true },
      bucket: environment.storage.bucket,
      config: {
        endpoint: environment.storage.endpoint,
        region: environment.storage.region,
        forcePathStyle: true,
        credentials: {
          accessKeyId: environment.storage.accessKeyId,
          secretAccessKey: environment.storage.secretAccessKey,
        },
      },
    }),
  ],
})
