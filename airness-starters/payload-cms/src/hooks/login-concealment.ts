// A failed sign-in tells nobody whether the account exists: not by its message, its status or its timing.
//
// Payload answers a locked account with "This user is locked due to having too many failed login attempts", which
// confirms that the address holds an account, and it answers an unknown address and a locked account before it
// hashes the password, which an existing unlocked account pays for (`src/lib/password-hash.ts`). These two hooks
// close both channels: one gives every refused sign-in the same answer, the other gives it the same cost.
import type { AfterErrorHook, CollectionBeforeOperationHook, PayloadRequest } from 'payload'
import { AuthenticationError, formatErrors, LockedAuth } from 'payload'
import { operationOf, originalErrorOf, textFieldOf } from '@/lib/hook-argument'
import { decoySalt, derivePasswordHash, isRefusedBeforeHashing } from '@/lib/password-hash'
import type { User } from '@/payload-types'

const isLockedAccount = (error: Error): boolean => originalErrorOf(error) instanceof LockedAuth

// Through GraphQL the answer is a GraphQL error rather than a REST body.
export const concealLockedAccount: AfterErrorHook = ({ error, graphqlResult, req }) => {
  if (!isLockedAccount(error)) {
    return {}
  }
  const refusal: AuthenticationError = new AuthenticationError(req.t)
  if (graphqlResult === undefined) {
    return { response: formatErrors(refusal), status: refusal.status }
  }
  return {
    graphqlResult: {
      ...graphqlResult,
      message: refusal.message,
      extensions: { ...graphqlResult.extensions, name: refusal.name, statusCode: refusal.status },
    },
  }
}

const accountNamed = async (req: PayloadRequest, email: string): Promise<User | undefined> => {
  const found: { readonly docs: User[] } = await req.payload.find({
    collection: 'users',
    where: { email: { equals: email } },
    depth: 0,
    limit: 1,
    showHiddenFields: true,
    req,
  })
  return found.docs[0]
}

// Payload compares the email it was given lowercased and trimmed, so the lookup reads it the same way.
export const equalizeLoginCost: CollectionBeforeOperationHook = async (hookArgument) => {
  const { args, req } = hookArgument
  const email: string | undefined = textFieldOf(args, 'email')
  const submitted: string | undefined = textFieldOf(args, 'password')
  if (email !== undefined && submitted !== undefined && operationOf(hookArgument) === 'login') {
    const candidate: User | undefined = await accountNamed(req, email.toLowerCase().trim())
    if (isRefusedBeforeHashing(candidate, new Date())) {
      await derivePasswordHash(submitted, decoySalt())
    }
  }
  return args
}
