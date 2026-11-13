import htmx from 'htmx.org'
import { enableBoard } from '@/board'
import { openNamedDialog } from '@/dialogs'
import { enablePreview } from '@/preview'

// htmx otherwise writes a style element for its request indicators, which the page's content security
// policy refuses, and it would evaluate attribute code no template of this application uses. Every swap runs
// inside a view transition, so a page changes with a short crossfade instead of a jump, which the stylesheet
// turns off for a reader who asks for reduced motion.
// eslint-disable-next-line functional/immutable-data -- htmx reads its configuration from this object when the document is ready
Object.assign(htmx.config, { includeIndicatorStyles: false, allowEval: false, globalViewTransitions: true })

// The shell boosts every link and form, so moving between pages swaps the body instead of reloading the
// document. htmx swaps no answer of a failed request by default, which would leave a reader on the page they
// left with nothing said, so a boosted navigation shows the failure page the server answered with.
const carriesDetail = (event: Event): event is CustomEvent<unknown> => event instanceof CustomEvent

document.addEventListener('htmx:beforeSwap', (event: Event): void => {
  const detail: unknown = carriesDetail(event) ? event.detail : undefined
  if (typeof detail === 'object' && detail !== null && 'boosted' in detail && detail.boosted === true) {
    // eslint-disable-next-line functional/immutable-data -- htmx reads whether to swap from the event it dispatched
    Object.assign(detail, { shouldSwap: true, isError: false })
  }
})

document.addEventListener('click', openNamedDialog)
enableBoard(document)
enablePreview(document)
