// Runs one command against services of its own, which the harness wraps around every test, build and
// browser gate of this project.
//
// The application reads its database from APP_DATABASE_URL, _USERNAME and _PASSWORD, writes its first
// administrator from APP_ADMINISTRATOR_EMAIL and _PASSWORD, stores files in the bucket APP_STORAGE_* names and
// has their pictures rendered by the server APP_IMAGES_* names. Each invocation starts a disposable PostgreSQL,
// RustFS and imgproxy through the harness's services helper, on a network of the run's own, named for this run
// and published on loopback ports Docker chooses, with credentials of this run's own, hands the command those
// values, and removes the containers afterwards, also when the run is stopped or killed. imgproxy reads the
// bucket over that network. The images are the ones compose.yaml and the Java test suite run.
import { randomBytes } from 'node:crypto'
import { randomSecret, withServices } from '@airness/node/services'

const DATABASE: string = 'starter'
const USER: string = 'starter'
const databaseCredential: string = randomSecret()
const administratorCredential: string = `Starter-${randomSecret()}`
const BUCKET: string = 'starter'
const STORAGE_KEY: string = 'starter'
const STORAGE_PORT: number = 9000
const storageCredential: string = randomSecret()
// imgproxy reads its signing key and salt as hexadecimal.
const SIGNING_BYTES: number = 32
const signingKey: string = randomBytes(SIGNING_BYTES).toString('hex')
const signingSalt: string = randomBytes(SIGNING_BYTES).toString('hex')
// imgproxy reaches RustFS by its name on the run's private network, which no other machine can join.
const STORAGE_ON_NETWORK: string = `http://objects:${String(STORAGE_PORT)}`

process.exitCode = await withServices({
  project: 'starter-verification',
  network: true,
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
      alias: 'objects',
      image: 'rustfs/rustfs:1.0.6',
      environment: { RUSTFS_ACCESS_KEY: STORAGE_KEY, RUSTFS_SECRET_KEY: storageCredential },
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
    {
      key: 'images',
      alias: 'images',
      image: 'darthsim/imgproxy:v4.0.24',
      environment: {
        IMGPROXY_KEY: signingKey,
        IMGPROXY_SALT: signingSalt,
        IMGPROXY_USE_S3: 'true',
        IMGPROXY_S3_ENDPOINT: STORAGE_ON_NETWORK,
        IMGPROXY_S3_REGION: 'us-east-1',
        IMGPROXY_S3_ALLOWED_BUCKETS: BUCKET,
        AWS_ACCESS_KEY_ID: STORAGE_KEY,
        AWS_SECRET_ACCESS_KEY: storageCredential,
        IMGPROXY_ALLOWED_SOURCES: 's3://',
        IMGPROXY_KEEP_COPYRIGHT: 'false',
        IMGPROXY_MAX_SRC_FILE_SIZE: '26214400',
        IMGPROXY_ENABLE_DEBUG_HEADERS: 'true',
      },
      ports: ['8080/tcp'],
      ready: ['imgproxy', 'health'],
    },
  ],
  environment: (services) => ({
    APP_DATABASE_URL: `jdbc:postgresql://127.0.0.1:${String(services.port('db', '5432/tcp'))}/${DATABASE}`,
    APP_DATABASE_USERNAME: USER,
    APP_DATABASE_PASSWORD: databaseCredential,
    APP_ADMINISTRATOR_EMAIL: 'administrator@starter.test',
    APP_ADMINISTRATOR_PASSWORD: administratorCredential,
    APP_STORAGE_ENDPOINT: `http://127.0.0.1:${String(services.port('objects', '9000/tcp'))}`,
    APP_STORAGE_BUCKET: BUCKET,
    APP_STORAGE_ACCESS_KEY: STORAGE_KEY,
    APP_STORAGE_SECRET_KEY: storageCredential,
    APP_IMAGES_URL: `http://127.0.0.1:${String(services.port('images', '8080/tcp'))}`,
    APP_IMAGES_KEY: signingKey,
    APP_IMAGES_SALT: signingSalt,
  }),
})
