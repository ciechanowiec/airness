// The application always keeps an account that can administer it.
//
// Only admins create accounts, and the admin panel admits only admins and editors, so without an admin nobody can
// grant the role again: the only way back is editing the database by hand. Two moments could leave the
// application there. The first account, registered on the create-first-user screen, would take the prefilled
// `user` role; and the last admin could give up the role or delete their own account.
import type { CollectionBeforeChangeHook, CollectionBeforeDeleteHook, PayloadRequest } from 'payload'
import { APIError, ValidationError } from 'payload'
import type { User } from '@/payload-types'

const ADMIN: User['roles'][number] = 'admin'
const BAD_REQUEST: number = 400

// Access is decided before `beforeChange` runs, and `users` lets only admins create, so the one create that reaches
// this hook through the REST API without a signed-in caller is Payload's own first-user registration, which creates
// with access overridden. A create through the Local API, such as the seed or a test, keeps the roles it names.
export const firstUserIsAdmin: CollectionBeforeChangeHook<User> = ({ data, operation, req }) =>
  operation === 'create' && req.payloadAPI === 'REST' && req.user === null ? { ...data, roles: [ADMIN] } : data

const otherAdmins = async (req: PayloadRequest, id: number | string): Promise<number> => {
  const counted: { readonly totalDocs: number } = await req.payload.count({
    collection: 'users',
    where: { and: [{ roles: { in: [ADMIN] } }, { id: { not_equals: id } }] },
    req,
  })
  return counted.totalDocs
}

const LAST_ADMIN: string = 'The application needs at least one admin. Make another account an admin first.'

export const keepAnAdmin: CollectionBeforeChangeHook<User> = async ({ data, operation, originalDoc, req }) => {
  const isLosingAdmin: boolean =
    operation === 'update' &&
    originalDoc?.roles.includes(ADMIN) === true &&
    data.roles !== undefined &&
    !data.roles.includes(ADMIN)
  if (isLosingAdmin && originalDoc !== undefined && (await otherAdmins(req, originalDoc.id)) === 0) {
    throw new ValidationError({ collection: 'users', errors: [{ message: LAST_ADMIN, path: 'roles' }] })
  }
  return data
}

export const keepAnAdminOnDelete: CollectionBeforeDeleteHook = async ({ id, req }) => {
  const account: User = await req.payload.findByID({ collection: 'users', id, depth: 0, req })
  if (account.roles.includes(ADMIN) && (await otherAdmins(req, id)) === 0) {
    throw new APIError(LAST_ADMIN, BAD_REQUEST, undefined, true)
  }
}
