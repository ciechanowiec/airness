import type { Access, CollectionConfig, FieldAccess } from 'payload'

import type { User } from '@/payload-types'

// Reusable access control helpers (conventions: .claude/skills/payload/reference/ACCESS-CONTROL.md).

/** Public - anyone, authenticated or not. Use sparingly (e.g. Media.read). */
export const anyone: Access = () => true

/** No caller at all, for an operation only server code performs, through the Local API with access overridden. */
export const nobody: Access = () => false

/** True when the user holds the admin role. The one admin predicate behind every admin-only rule below. */
const isAdmin = (user: User | null): boolean => Boolean(user?.roles.includes('admin'))

/** Admins only. */
export const admins: Access = ({ req: { user } }) => isAdmin(user)

/** True when the user holds the admin or editor role. The role predicate behind `adminsOrEditors`. */
const isAdminOrEditor = (user: User | null): boolean =>
  Boolean(user?.roles.some((role) => role === 'admin' || role === 'editor'))

type BooleanAccess = NonNullable<NonNullable<CollectionConfig['access']>['admin']>

/**
 * Admins or editors. Boolean-only, like the collection `admin` (panel) access, which cannot return a `Where`
 * filter; a predicate of this shape is also assignable to `Access`, so it serves both the admin-panel gate
 * (Users.admin) and CRUD rules (Media create/update/delete).
 */
export const adminsOrEditors: BooleanAccess = ({ req: { user } }) => isAdminOrEditor(user)

/**
 * Admins and editors read every document, drafts included; everyone else reads only what has been published. The
 * `Where` keeps an unpublished draft out of every list and lookup a visitor or a signed-in reader makes.
 */
export const publishedOrEditors: Access = ({ req: { user } }) =>
  isAdminOrEditor(user) || { _status: { equals: 'published' } }

/** Admins see everything; other users are constrained to their own record. */
export const adminsOrSelf: Access = ({ req: { user } }) => (user ? isAdmin(user) || { id: { equals: user.id } } : false)

/** Field-level: only admins (boolean only - no Where filters at field level). */
export const adminsFieldLevel: FieldAccess = ({ req: { user } }) => isAdmin(user)
