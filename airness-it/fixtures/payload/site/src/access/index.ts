// The access rules the collections reference by name, so each has a seam a unit test reaches.
import type { Access } from 'payload'

/** Grants the operation to everyone, an unauthenticated caller included. */
export const anyone: Access = (): boolean => true

/** Refuses the operation to everyone, so the configuration decides rather than inheriting a default. */
export const nobody: Access = (): boolean => false
