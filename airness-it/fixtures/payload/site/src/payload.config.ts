// The configuration Payload boots, and the one the payload-generated and payload-defaults gates import.
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { postgresAdapter } from '@payloadcms/db-postgres'
import { buildConfig } from 'payload'
import sharp from 'sharp'
import { Posts } from '@/collections/Posts'
import { Users } from '@/collections/Users'
import { type Environment, loadEnvironment } from '@/lib/environment'

const { payloadSecret, databaseUrl }: Environment = loadEnvironment()
const directory: string = path.dirname(fileURLToPath(import.meta.url))

const config: ReturnType<typeof buildConfig> = buildConfig({
  admin: { user: Users.slug, importMap: { baseDir: directory } },
  collections: [Users, Posts],
  db: postgresAdapter({ pool: { connectionString: databaseUrl }, push: false }),
  secret: payloadSecret,
  sharp,
  typescript: { outputFile: path.join(directory, 'payload-types.ts') },
})

export default config
