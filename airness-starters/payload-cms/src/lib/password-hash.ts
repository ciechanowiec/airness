// The cost a failed sign-in has to pay whether or not the account exists.
//
// Payload answers an unknown email, and a locked account, before it hashes the password, while an existing
// unlocked account pays a PBKDF2 derivation of `ITERATIONS` iterations first. The difference is long enough to
// read off a single response, so it tells a stranger which addresses hold an account. The login hook in
// `src/hooks/login-concealment.ts` closes that gap by deriving a hash of the same cost on the two fast paths, and
// this module is the derivation, kept pure so a test can hold it to the exact parameters Payload stores.
//
// Payload exports neither its parameters nor its derivation, so they are restated here. An integration test
// derives a stored hash from its salt with this function and compares the two, so a Payload release that changes
// any parameter fails that test rather than quietly reopening the gap.
import { randomBytes } from 'node:crypto'

/** The prefix Payload writes before the hex digest of a current-format password hash. */
const HASH_PREFIX: string = 'pbkdf2-sha256-v1:'
const ITERATIONS: number = 600_000
const KEY_LENGTH_BYTES: number = 32
const BITS_PER_BYTE: number = 8
const KEY_LENGTH_BITS: number = KEY_LENGTH_BYTES * BITS_PER_BYTE
const DIGEST: string = 'SHA-256'
const SALT_LENGTH_BYTES: number = 32

/**
 * Derive a password hash exactly as Payload stores one.
 * @param password the password as submitted.
 * @param salt the salt, as the hex text Payload stores beside the hash.
 * @returns the hash, prefixed the way Payload prefixes it.
 */
export const derivePasswordHash = async (password: string, salt: string): Promise<string> => {
  // Payload hands both to PBKDF2 as text, which Node encodes as UTF-8; the salt is the hex text it stores.
  const encoder: TextEncoder = new TextEncoder()
  const key: CryptoKey = await crypto.subtle.importKey('raw', encoder.encode(password), 'PBKDF2', false, ['deriveBits'])
  const derived: ArrayBuffer = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', hash: DIGEST, salt: encoder.encode(salt), iterations: ITERATIONS },
    key,
    KEY_LENGTH_BITS,
  )
  return `${HASH_PREFIX}${Buffer.from(derived).toString('hex')}`
}

/**
 * A fresh salt for a derivation whose result nobody compares, the shape Payload's own salts take.
 * @returns the salt as hex text.
 */
export const decoySalt = (): string => randomBytes(SALT_LENGTH_BYTES).toString('hex')

/** The part of an account the sign-in decision reads. */
export interface LoginCandidate {
  readonly lockUntil?: string | null | undefined
}

/**
 * Whether Payload will refuse a sign-in before it hashes the password, so the hook has to pay that cost instead.
 * @param candidate the account the submitted email names, or undefined when none does.
 * @param now the moment of the attempt.
 * @returns true when no account matches or the matching account is locked.
 */
export const isRefusedBeforeHashing = (candidate: LoginCandidate | undefined, now: Date): boolean => {
  if (candidate === undefined) {
    return true
  }
  const { lockUntil } = candidate
  return typeof lockUntil === 'string' && new Date(lockUntil).getTime() > now.getTime()
}
