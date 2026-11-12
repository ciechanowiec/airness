// The schema of the one table the server reads, which an empty database is brought to before anything reads it.
import { execute } from './database.ts'

export const migrate = async (databaseUrl: string): Promise<void> => {
  await execute(databaseUrl, 'CREATE TABLE IF NOT EXISTS greeting (word text NOT NULL)')
}
