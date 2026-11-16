// The categories the public site shows.
import type { PaginatedDocs, Payload } from 'payload'

import { type CategorySummary, toCategorySummary } from '@/lib/views'
import type { Category } from '@/payload-types'
import { payloadClient } from './payload'

/** The most categories a list shows; a site with more groups them differently. */
const CATEGORY_LIMIT: number = 50
const ONE: number = 1

/**
 * Every category, by title.
 * @returns the categories.
 */
export const findCategories = async (): Promise<readonly CategorySummary[]> => {
  const payload: Payload = await payloadClient()
  const result: PaginatedDocs<Category> = await payload.find({
    collection: 'categories',
    sort: 'title',
    limit: CATEGORY_LIMIT,
    depth: 0,
    overrideAccess: false,
  })
  return result.docs.map((category: Category): CategorySummary => toCategorySummary(category))
}

/**
 * The category a slug names.
 * @param slug the category's slug.
 * @returns the category, or undefined when none has the slug.
 */
export const findCategory = async (slug: string): Promise<CategorySummary | undefined> => {
  const payload: Payload = await payloadClient()
  const result: PaginatedDocs<Category> = await payload.find({
    collection: 'categories',
    where: { slug: { equals: slug } },
    limit: ONE,
    depth: 0,
    overrideAccess: false,
  })
  const category: Category | undefined = result.docs[0]
  return category === undefined ? undefined : toCategorySummary(category)
}
