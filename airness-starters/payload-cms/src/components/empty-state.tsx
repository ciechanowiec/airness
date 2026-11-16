import { Inbox } from 'lucide-react'

/** What an empty state says. */
export interface EmptyStateProperties {
  readonly title: string
  readonly message: string
}

/**
 * What a list shows when it holds nothing, so an empty page says why rather than looking broken.
 * @param properties the title and the message.
 * @returns the empty state.
 */
export function EmptyState(properties: Readonly<EmptyStateProperties>) {
  const { title, message } = properties

  return (
    <div className="flex flex-col items-center gap-3 rounded-box border border-base-300 border-dashed px-6 py-16 text-center">
      <Inbox aria-hidden="true" className="size-10 text-primary" />
      <h2 className="font-semibold font-serif text-2xl">{title}</h2>
      <p className="max-w-measure text-base-content/70">{message}</p>
    </div>
  )
}
