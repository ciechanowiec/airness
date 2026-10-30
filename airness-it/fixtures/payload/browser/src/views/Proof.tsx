import type { AdminViewServerProps } from 'payload'
import { DefaultTemplate } from '@payloadcms/next/templates'

/** A real custom admin view, rendered inside Payload's own default template. */
const CONTENT_PADDING: number = 32

export function Proof(props: AdminViewServerProps): React.JSX.Element {
  return (
    <DefaultTemplate {...props} visibleEntities={props.initPageResult.visibleEntities}>
      <main style={{ padding: CONTENT_PADDING, color: '#111', background: '#fff' }}>
        <h1>Owned view</h1>
        <p id="owned-copy">This content belongs to the application.</p>
      </main>
    </DefaultTemplate>
  )
}
