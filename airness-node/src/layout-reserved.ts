// What a replaced element reserves before its source arrives, measured in the page.
//
// The function is handed to the page with the rest of the layout tools, by its source, so it reads nothing
// outside its own parameters.

/** An element's own declarations, read and written through the CSS object model. */
export interface PageInlineStyle {
  readonly getPropertyValue: (property: string) => string
  readonly getPropertyPriority: (property: string) => string
  readonly setProperty: (property: string, value: string, priority: string) => void
  readonly removeProperty: (property: string) => string
}

/** What a measured element offers the measurement. */
export interface ReservableElement {
  readonly style: PageInlineStyle
  readonly getAttribute: (name: string) => string | null
  readonly cloneNode: (isDeep: boolean) => ReservableElement
  readonly removeAttribute: (name: string) => void
  readonly setAttribute: (name: string, value: string) => void
  readonly before: (node: ReservableElement) => void
  readonly remove: () => void
  readonly getBoundingClientRect: () => { readonly width: number; readonly height: number }
}

// The space a replaced element reserves before its source arrives is the box the same element takes
// with no source at all. A shallow copy with every source removed takes the element's own slot while the
// element is out of layout, and is read and removed again in the same task, so nothing is painted. Laid
// out beside the element instead, a flex or grid row would share its space between the two.
//
// Both are hidden, and the element restored, through the CSS object model, which a content security policy
// refusing inline style does not govern. A style attribute written instead is refused: it was reported as the
// page's own violation, and a refused restore left the element hidden, so the page moved under the scan.
/**
 * The box the element takes with no source at all.
 * @param element the replaced element.
 * @returns the reserved box.
 */
export const reservedBoxOf = (element: ReservableElement): { readonly width: number; readonly height: number } => {
  const placeholder: ReservableElement = element.cloneNode(false)
  for (const source of ['src', 'srcset', 'poster', 'srcdoc', 'data']) {
    placeholder.removeAttribute(source)
  }
  placeholder.removeAttribute('id')
  placeholder.setAttribute('alt', '')
  placeholder.setAttribute('aria-hidden', 'true')
  const hasOwnStyle: boolean = element.getAttribute('style') !== null
  const display: string = element.style.getPropertyValue('display')
  const priority: string = element.style.getPropertyPriority('display')
  placeholder.style.setProperty('visibility', 'hidden', 'important')
  element.style.setProperty('display', 'none', 'important')
  element.before(placeholder)
  const measured: { readonly width: number; readonly height: number } = placeholder.getBoundingClientRect()
  placeholder.remove()
  if (!hasOwnStyle) {
    element.removeAttribute('style')
  } else if (display === '') {
    element.style.removeProperty('display')
  } else {
    element.style.setProperty('display', display, priority)
  }
  return measured
}
