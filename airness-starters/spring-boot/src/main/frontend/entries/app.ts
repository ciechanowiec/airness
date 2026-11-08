import htmx from 'htmx.org'
import { openNamedDialog } from '@/dialogs'

// htmx otherwise writes a style element for its request indicators, which the page's content security
// policy refuses, and it would evaluate attribute code no template of this application uses.
// eslint-disable-next-line functional/immutable-data -- htmx reads its configuration from this object when the document is ready
Object.assign(htmx.config, { includeIndicatorStyles: false, allowEval: false })

document.addEventListener('click', openNamedDialog)
