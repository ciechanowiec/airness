'use client'

import { RefreshRouteOnSave } from '@payloadcms/live-preview-react'
import { useRouter } from 'next/navigation'
import { useCallback } from 'react'

/** Where the admin panel that sends the save events is served. */
export interface LivePreviewRefreshProperties {
  readonly serverUrl: string
}

/**
 * Keeps the draft preview in step with the editor beside it. When the admin panel's Live Preview saves the draft, it
 * tells this frame, which then renders the page again on the server with the saved draft. It draws nothing itself.
 * @param properties the origin of the admin panel, the only one whose messages it follows.
 * @returns the listener.
 */
export function LivePreviewRefresh(properties: Readonly<LivePreviewRefreshProperties>) {
  const { serverUrl } = properties
  const router: ReturnType<typeof useRouter> = useRouter()
  const refresh: () => void = useCallback((): void => {
    router.refresh()
  }, [router])

  return <RefreshRouteOnSave refresh={refresh} serverURL={serverUrl} />
}
