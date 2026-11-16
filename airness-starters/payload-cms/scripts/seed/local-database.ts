import { loadServerEnvironment } from '@/lib/environment'

const LOCAL_DATABASE_HOSTS: ReadonlySet<string> = new Set(['127.0.0.1', 'localhost', '::1'])

/**
 * The host of the database the seed would write to, read from the validated environment.
 * @returns the host name.
 */
export const databaseHost = (): string => new URL(loadServerEnvironment().databaseUrl).hostname

/**
 * Whether a database host is this machine. A seed writes only there, with no override, so neither a known
 * password nor demo content can reach a remote or production database by any path.
 * @param host the database host.
 * @returns true for a loopback host.
 */
export const isLocalDatabase = (host: string): boolean => LOCAL_DATABASE_HOSTS.has(host)
