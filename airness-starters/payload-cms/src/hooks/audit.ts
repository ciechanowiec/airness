// The hooks that write the audit trail. What an event holds is decided in `src/lib/audit-event.ts`; these hooks
// observe the moment each event happens and record it.
//
// An event is written through the request it describes, so it joins that request's transaction: a change that is
// rolled back leaves no event claiming it happened. The `audit-events` collection refuses every caller, and the write
// passes because a Local API call runs with access overridden unless it says otherwise, which is the only way a
// document of it comes into being.
import type {
  AfterErrorHook,
  CollectionAfterChangeHook,
  CollectionAfterDeleteHook,
  CollectionAfterLoginHook,
  CollectionAfterLogoutHook,
  CollectionAfterOperationHook,
  CollectionBeforeChangeHook,
  CollectionBeforeOperationHook,
  PayloadRequest,
} from 'payload'
import { AuthenticationError, LockedAuth } from 'payload'
import {
  type AccountIdentity,
  ANONYMOUS_ACTOR,
  type AuditEventInput,
  accountChanges,
  accountTarget,
  rolesDetail,
  UNNAMED_TARGET,
} from '@/lib/audit-event'
import { operationOf, originalErrorOf, textFieldOf } from '@/lib/hook-argument'
import type { User } from '@/payload-types'

/**
 * Write one event into the trail, inside the transaction of the request that caused it.
 * @param req the request the event belongs to.
 * @param event the event.
 */
const recordAuditEvent = async (req: PayloadRequest, event: AuditEventInput): Promise<void> => {
  await req.payload.create({ collection: 'audit-events', data: event, depth: 0, req })
}

const recordIfAny = async (req: PayloadRequest, event: AuditEventInput | undefined): Promise<void> => {
  if (event !== undefined) {
    await recordAuditEvent(req, event)
  }
}

const actorOf = (req: PayloadRequest): string => req.user?.email ?? ANONYMOUS_ACTOR

// The email an operation's caller named, which is what a sign-in, an unlock or a reset request is aimed at.
const requestedEmail = (operationArguments: object): string =>
  textFieldOf(operationArguments, 'email') ?? UNNAMED_TARGET

export const auditLogin: CollectionAfterLoginHook<User> = async ({ req, user }) => {
  await recordAuditEvent(req, { action: 'login', outcome: 'success', actor: user.email, target: accountTarget(user) })
}

export const auditLogout: CollectionAfterLogoutHook<User> = async ({ req }) => {
  const actor: string = actorOf(req)
  await recordAuditEvent(req, {
    action: 'logout',
    outcome: 'success',
    actor,
    target: req.user === null ? actor : accountTarget(req.user),
  })
}

// The authentication attempt a request makes, noted before its operation runs. A refusal reaches only the root error
// hooks, which are not handed the operation's arguments, and the request body is no place to read them from: a
// GraphQL request carries them inside its query. `req.context` is the one object both see, over REST and GraphQL.
const ATTEMPT_KEY: string = 'audit:authentication-attempt'

interface AuthenticationAttempt {
  readonly operation: 'login' | 'resetPassword'
  readonly target: string
}

// Payload compares the email it was given lowercased and trimmed, so the event names it the same way.
const loginTarget = (operationArguments: object): string =>
  textFieldOf(operationArguments, 'email')?.toLowerCase().trim() ?? UNNAMED_TARGET

// A reset names its account only by the token mailed to it. The token is looked up whether or not it has expired,
// so an attempt with a stale link still names the account the link was for.
const resetTarget = async (req: PayloadRequest, operationArguments: object): Promise<string> => {
  const token: string | undefined = textFieldOf(operationArguments, 'token')
  if (token === undefined) {
    return UNNAMED_TARGET
  }
  const found: { readonly docs: User[] } = await req.payload.find({
    collection: 'users',
    where: { resetPasswordToken: { equals: token } },
    depth: 0,
    limit: 1,
    req,
  })
  const account: User | undefined = found.docs[0]
  return account === undefined ? UNNAMED_TARGET : accountTarget(account)
}

const attemptOf = async (
  operation: string,
  operationArguments: object,
  req: PayloadRequest,
): Promise<AuthenticationAttempt | undefined> => {
  switch (operation) {
    case 'login': {
      return { operation: 'login', target: loginTarget(operationArguments) }
    }
    case 'resetPassword': {
      return { operation: 'resetPassword', target: await resetTarget(req, operationArguments) }
    }
    default: {
      return undefined
    }
  }
}

export const noteAuthenticationAttempt: CollectionBeforeOperationHook = async (hookArgument) => {
  const { args, req } = hookArgument
  const attempt: AuthenticationAttempt | undefined = await attemptOf(operationOf(hookArgument), args, req)
  if (attempt !== undefined) {
    req.context[ATTEMPT_KEY] = attempt
  }
  return args
}

const notedAttempt = (req: PayloadRequest): AuthenticationAttempt | undefined => {
  const noted: unknown = req.context[ATTEMPT_KEY]
  if (typeof noted !== 'object' || noted === null) {
    return undefined
  }
  const operation: unknown = Reflect.get(noted, 'operation')
  const target: unknown = Reflect.get(noted, 'target')
  return typeof target === 'string' && (operation === 'login' || operation === 'resetPassword')
    ? { operation, target }
    : undefined
}

// Payload refuses a login with `AuthenticationError`, or `LockedAuth` for a locked account, and throws neither
// anywhere else.
const isLoginRefusal = (error: Error): boolean => {
  const original: unknown = originalErrorOf(error)
  return original instanceof AuthenticationError || original instanceof LockedAuth
}

const failureEvent = (error: Error, attempt: AuthenticationAttempt | undefined): AuditEventInput | undefined => {
  if (attempt?.operation === 'login' && isLoginRefusal(error)) {
    return { action: 'login-failed', outcome: 'failure', actor: ANONYMOUS_ACTOR, target: attempt.target }
  }
  if (attempt?.operation === 'resetPassword') {
    return { action: 'password-reset', outcome: 'failure', actor: ANONYMOUS_ACTOR, target: attempt.target }
  }
  return undefined
}

// A root hook rather than a collection one, because a GraphQL request reaches only the root hooks.
export const auditAuthenticationFailure: AfterErrorHook = async ({ error, req }) => {
  await recordIfAny(req, failureEvent(error, notedAttempt(req)))
}

// Payload removes the new password from the data before `afterChange` runs, so the change is noted here, on the
// request's context, under the id of the account it belongs to.
const passwordChangeKey = (id: number | string): string => `audit:password-changed:${String(id)}`

export const notePasswordChange: CollectionBeforeChangeHook<User> = ({ data, operation, originalDoc, req }): void => {
  if (operation === 'update' && originalDoc !== undefined && typeof data.password === 'string') {
    req.context[passwordChangeKey(originalDoc.id)] = true
  }
}

interface AccountChange {
  readonly doc: User
  readonly previousDoc: User
  readonly isCreation: boolean
  readonly req: PayloadRequest
}

const accountChangeEvent = ({ doc, previousDoc, isCreation, req }: AccountChange): AuditEventInput | undefined => {
  const base: Pick<AuditEventInput, 'actor' | 'outcome' | 'target'> = {
    actor: actorOf(req),
    outcome: 'success',
    target: accountTarget(doc),
  }
  if (isCreation) {
    const detail: string | undefined = rolesDetail(doc)
    return { ...base, action: 'account-created', ...(detail !== undefined && { detail }) }
  }
  const changes: string[] = accountChanges(previousDoc, doc, req.context[passwordChangeKey(doc.id)] === true)
  if (changes.length === 0) {
    return undefined
  }
  const action: AuditEventInput['action'] = changes.some((change: string): boolean => change.startsWith('roles:'))
    ? 'roles-changed'
    : 'account-updated'
  return { ...base, action, detail: changes.join('; ') }
}

export const auditAccountChange: CollectionAfterChangeHook<User> = async ({ doc, operation, previousDoc, req }) => {
  await recordIfAny(req, accountChangeEvent({ doc, previousDoc, isCreation: operation === 'create', req }))
  return doc
}

export const auditAccountDeletion: CollectionAfterDeleteHook<User> = async ({ doc, req }) => {
  await recordAuditEvent(req, {
    action: 'account-deleted',
    outcome: 'success',
    actor: actorOf(req),
    target: accountTarget(doc),
  })
  return doc
}

// A reset answers with the account it signed in, which is the one its event names.
const resetAccount = (result: unknown): AccountIdentity | undefined => {
  const user: unknown = typeof result === 'object' && result !== null ? Reflect.get(result, 'user') : undefined
  if (typeof user !== 'object' || user === null) {
    return undefined
  }
  const id: unknown = Reflect.get(user, 'id')
  const email: unknown = Reflect.get(user, 'email')
  return typeof email === 'string' && (typeof id === 'number' || typeof id === 'string') ? { id, email } : undefined
}

const passwordResetEvent = (result: unknown): AuditEventInput | undefined => {
  const account: AccountIdentity | undefined = resetAccount(result)
  return account === undefined
    ? undefined
    : { action: 'password-reset', outcome: 'success', actor: account.email, target: accountTarget(account) }
}

interface Recovery {
  readonly operation: string
  readonly operationArguments: object
  readonly result: unknown
  readonly req: PayloadRequest
}

// Unlocking and both halves of password recovery change an account without passing through `afterChange`, so
// they are recorded once their operation succeeds.
const recoveryEvent = ({ operation, operationArguments, result, req }: Recovery): AuditEventInput | undefined => {
  switch (operation) {
    case 'unlock': {
      const target: string = requestedEmail(operationArguments)
      return { action: 'account-unlocked', outcome: 'success', actor: actorOf(req), target }
    }
    case 'forgotPassword': {
      const target: string = requestedEmail(operationArguments)
      return { action: 'password-reset-requested', outcome: 'success', actor: ANONYMOUS_ACTOR, target }
    }
    case 'resetPassword': {
      return passwordResetEvent(result)
    }
    default: {
      return undefined
    }
  }
}

export const auditAccountRecovery: CollectionAfterOperationHook<'users'> = async (hookArgument) => {
  const { args, req, result } = hookArgument
  await recordIfAny(req, recoveryEvent({ operation: operationOf(hookArgument), operationArguments: args, result, req }))
  return result
}
