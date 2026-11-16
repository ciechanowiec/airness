import Link from 'next/link'

import type { CategorySummary } from '@/lib/views'

/** What a category chip shows. */
export interface CategoryChipProperties {
  readonly category: CategorySummary
  /** Whether the chip links the category's page; inside a card that is itself a link, it only names it. */
  readonly linked?: boolean
}

const CHIP_CLASS: string =
  'inline-flex min-h-7 items-center rounded-selector bg-base-200 px-3 font-semibold text-primary text-xs uppercase tracking-wider'

/**
 * A small label naming a post's category, a link to the category's page unless it sits inside another link.
 * @param properties the category, and whether the chip links it.
 * @returns the chip.
 */
export function CategoryChip(properties: Readonly<CategoryChipProperties>) {
  const { category, linked = true } = properties

  return linked ? (
    <Link className={`${CHIP_CLASS} hover:bg-base-300`} href={category.href}>
      {category.title}
    </Link>
  ) : (
    <span className={CHIP_CLASS}>{category.title}</span>
  )
}
