// Runs one command against services of its own, which the harness wraps around every test, build and
// browser gate of this project.
//
// Each invocation starts a PostgreSQL and a RustFS through the harness's services helper, with credentials
// and a Payload signing secret of this run's own. Once both answer and the upload bucket exists, the
// committed migrations are applied, because the adapter never pushes a schema, and the demo content is seeded, so
// the browser sweep reaches every page template of a site with posts; then the command runs with
// the database and the bucket handed over in the variables the application reads. The development services
// compose.yaml starts are never touched.
import { execFileSync } from 'node:child_process'
import { randomSecret, withServices } from '@airness/node/services'

const DATABASE: string = 'starter'
// A production build refuses to start without its public origin. The end-to-end gate names the origin it serves
// the application on, in the variable package.json lists under airness.serverUrlVariables, and that one is kept;
// every other command, such as the production build, runs where the development origin stands in for it.
const SERVER_URL_VARIABLE: string = 'NEXT_PUBLIC_SERVER_URL'
const DEVELOPMENT_SERVER_URL: string = 'http://localhost:3000'
const serverUrl: string = process.env[SERVER_URL_VARIABLE] ?? DEVELOPMENT_SERVER_URL
const BUCKET: string = 'starter'
const USER: string = 'starter'
const databaseCredential: string = randomSecret()
const payloadSecret: string = randomSecret()
const storageAccessKey: string = randomSecret()
const storageSecretKey: string = randomSecret()

process.exitCode = await withServices({
  project: 'starter-verification',
  containers: [
    {
      key: 'db',
      image: 'postgres:18-alpine',
      environment: { POSTGRES_DB: DATABASE, POSTGRES_USER: USER, POSTGRES_PASSWORD: databaseCredential },
      ports: ['5432/tcp'],
      // PostgreSQL listens on its socket alone while the image initialises the volume, so readiness is
      // asked over TCP, which answers only once the server the application will reach is up.
      ready: ['pg_isready', '--host=127.0.0.1', `--username=${USER}`, `--dbname=${DATABASE}`],
    },
    {
      key: 'objects',
      image: 'rustfs/rustfs:1.0.5',
      environment: { RUSTFS_ACCESS_KEY: storageAccessKey, RUSTFS_SECRET_KEY: storageSecretKey },
      ports: ['9000/tcp'],
      command: ['/data'],
      // The image's own S3 client creates the bucket, idempotently, so this answers once RustFS accepts
      // requests and the bucket the application writes to exists. The credentials are read from the
      // container's environment rather than passed as arguments.
      ready: [
        'sh',
        '-c',
        `RC_CONFIG_DIR=/tmp/rc RC_HOST_local="http://$RUSTFS_ACCESS_KEY:$RUSTFS_SECRET_KEY@127.0.0.1:9000" rc bucket create local/${BUCKET} --ignore-existing`,
      ],
    },
  ],
  environment: (services) => ({
    S3_ENDPOINT: `http://127.0.0.1:${String(services.port('objects', '9000/tcp'))}`,
    S3_BUCKET: BUCKET,
    S3_ACCESS_KEY_ID: storageAccessKey,
    S3_SECRET_ACCESS_KEY: storageSecretKey,
    DATABASE_URL: `postgresql://${USER}:${databaseCredential}@127.0.0.1:${String(services.port('db', '5432/tcp'))}/${DATABASE}`,
    PAYLOAD_SECRET: payloadSecret,
    [SERVER_URL_VARIABLE]: serverUrl,
  }),
  prepare: (_services, environment) => {
    execFileSync('pnpm', ['run', 'migrate'], { env: { ...process.env, ...environment }, stdio: 'inherit' })
    execFileSync('pnpm', ['run', 'seed:content'], { env: { ...process.env, ...environment }, stdio: 'inherit' })
  },
})
