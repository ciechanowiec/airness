import Link from 'next/link'

import type { CategorySummary } from '@/lib/views'

/** What a category list shows. */
export interface CategoryListProperties {
  readonly categories: readonly CategorySummary[]
}

/**
 * Every category as a card with its description, the way into each category's posts.
 * @param properties the categories.
 * @returns the list, or nothing when there is no category.
 */
export function CategoryList(properties: Readonly<CategoryListProperties>) {
  const { categories } = properties

  if (categories.length === 0) {
    return null
  }
  return (
    <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {categories.map((category: CategorySummary) => (
        <li className="flex" key={category.slug}>
          <Link
            className="flex w-full flex-col gap-2 rounded-box border border-base-300 bg-base-200 p-5 hover:border-primary"
            href={category.href}
          >
            <span className="font-semibold font-serif text-xl">{category.title}</span>
            {category.description === '' ? null : (
              <span className="max-w-measure text-base-content/70 text-sm">{category.description}</span>
            )}
          </Link>
        </li>
      ))}
    </ul>
  )
}
