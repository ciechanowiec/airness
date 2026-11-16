import type { Metadata } from 'next'

import { NotFoundMessage } from '@/components/not-found-message'

export const metadata: Metadata = {
  title: 'Page not found',
}

export default function NotFound() {
  return <NotFoundMessage />
}
