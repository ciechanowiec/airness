// Runs one command against services of its own, which the harness wraps around every test, build and
// browser gate of this project.
//
// The application reads its database from APP_DATABASE_URL, _USERNAME and _PASSWORD and writes its first
// administrator from APP_ADMINISTRATOR_EMAIL and _PASSWORD. Each invocation starts a disposable PostgreSQL
// through the harness's services helper, named for this run and published on a loopback port Docker
// chooses, with credentials of this run's own, hands the command those values, and removes the container
// afterwards, also when the run is stopped or killed. The image is the one compose.yaml and the Java test
// suite run.
import { randomSecret, withServices } from '@airness/node/services'

const DATABASE: string = 'starter'
const USER: string = 'starter'
const databaseCredential: string = randomSecret()
const administratorCredential: string = `Starter-${randomSecret()}`

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
  ],
  environment: (services) => ({
    APP_DATABASE_URL: `jdbc:postgresql://127.0.0.1:${String(services.port('db', '5432/tcp'))}/${DATABASE}`,
    APP_DATABASE_USERNAME: USER,
    APP_DATABASE_PASSWORD: databaseCredential,
    APP_ADMINISTRATOR_EMAIL: 'administrator@starter.test',
    APP_ADMINISTRATOR_PASSWORD: administratorCredential,
  }),
})
