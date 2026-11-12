// Brings the database DATABASE_URL names to the schema, as the services wrapper does before every gate but the
// image gate, which hands the image its services empty.
import { migrate } from '../src/migrate.ts'

await migrate(process.env['DATABASE_URL'] ?? '')
