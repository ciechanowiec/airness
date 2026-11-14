// What an audit event records, and how an account is named in one.
//
// The audit trail answers, after an incident, who did what to which account, when, and whether it worked. Payload
// records none of it, so the hooks in `src/hooks/audit.ts` write one `audit-events` document per authentication
// outcome, account or permission change, and privileged operation. The document's `createdAt` is its timestamp:
// Payload stores it with its time zone and serves it in ISO 8601, so it names one instant unambiguously.

/** Every event the trail records. */
export const AUDIT_ACTIONS: readonly [
  'login',
  'login-failed',
  'logout',
  'account-created',
  'account-updated',
  'roles-changed',
  'account-deleted',
  'account-unlocked',
  'password-reset-requested',
  'password-reset',
] = [
  'login',
  'login-failed',
  'logout',
  'account-created',
  'account-updated',
  'roles-changed',
  'account-deleted',
  'account-unlocked',
  'password-reset-requested',
  'password-reset',
] as const

/** Whether the recorded attempt did what it set out to do. */
export const AUDIT_OUTCOMES: readonly ['success', 'failure'] = ['success', 'failure'] as const

/** One event the trail records. */
export interface AuditEventInput {
  readonly action: (typeof AUDIT_ACTIONS)[number]
  readonly outcome: (typeof AUDIT_OUTCOMES)[number]
  /** Who acted: the signed-in account's email, or {@link ANONYMOUS_ACTOR}. */
  readonly actor: string
  /** What the action was aimed at, such as {@link accountTarget} of an account. */
  readonly target: string
  readonly detail?: string
}

/** The actor of an event no signed-in account caused. */
export const ANONYMOUS_ACTOR: string = 'anonymous'

/** The target of an event whose request named no account, such as a sign-in without an email. */
export const UNNAMED_TARGET: string = 'unnamed'

/** The part of an account an event names it by. */
export interface AccountIdentity {
  readonly id: number | string
  readonly email: string
}

/**
 * Name an account in an event by its id and its email, so the event still says whose account it was once the
 * account is deleted or its email changes.
 * @param account the account.
 * @returns the target text.
 */
export const accountTarget = (account: AccountIdentity): string => `users/${String(account.id)} <${account.email}>`

/** The part of an account an update changes that the trail records. */
export interface AccountState {
  readonly email: string
  readonly name: string
  /**
   * Absent when the caller may not read roles: Payload removes a field its reader has no access to before the
   * hooks see the account, and such a caller cannot change roles either.
   */
  readonly roles?: readonly string[] | undefined
}

const rolesText = (roles: readonly string[]): string =>
  [...roles].toSorted((left: string, right: string): number => left.localeCompare(right)).join(', ')

/**
 * The roles an account was created with, as an event's detail.
 * @param account the new account.
 * @returns the detail, or undefined when the caller may not read roles.
 */
export const rolesDetail = (account: AccountState): string | undefined =>
  account.roles === undefined ? undefined : `roles: ${rolesText(account.roles)}`

const rolesChange = (before: AccountState, after: AccountState): string[] => {
  if (before.roles === undefined || after.roles === undefined) {
    return []
  }
  const [was, now]: readonly [string, string] = [rolesText(before.roles), rolesText(after.roles)]
  return was === now ? [] : [`roles: ${was} -> ${now}`]
}

/**
 * The recorded changes between two states of an account, one phrase per changed property.
 * @param before the account before the update.
 * @param after the account after it.
 * @param hasNewPassword whether the update set a new password, which no stored field shows.
 * @returns the phrases, empty when nothing the trail records changed.
 */
export const accountChanges = (before: AccountState, after: AccountState, hasNewPassword: boolean): string[] => [
  ...rolesChange(before, after),
  ...(before.email === after.email ? [] : [`email: ${before.email} -> ${after.email}`]),
  ...(before.name === after.name ? [] : ['name changed']),
  ...(hasNewPassword ? ['password changed'] : []),
]
