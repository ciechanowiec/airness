// The board drawn beside the pages a reader sees before signing in lets its cards be dragged between its
// columns, so the first thing a visitor can do is the thing the application is for. It all happens in the
// page: nothing is sent and nothing is kept.

const DRAGGING: string = 'data-dragging'

const TARGET: string = 'data-target'

// The classes the floating card of the preview carries while it is lifted out of its column, and the one a
// card resting in a column carries instead.
const LIFTED: readonly string[] = ['-rotate-2', 'shadow-lift', 'border', 'border-line']

const RESTING: string = 'press'

// Half a card's height, so a card lands before the first one whose middle lies below the pointer.
const MIDDLE: number = 0.5

const within = (target: EventTarget | null, selector: string): HTMLElement | undefined => {
  const found: Element | null = target instanceof Element ? target.closest(selector) : null
  return found instanceof HTMLElement ? found : undefined
}

const dragged = (preview: HTMLElement): HTMLElement | undefined => {
  const card: Element | null = preview.querySelector(`[${CSS.escape(DRAGGING)}]`)
  return card instanceof HTMLElement ? card : undefined
}

const clearTargets = (preview: HTMLElement): void => {
  preview.querySelectorAll(`[${CSS.escape(TARGET)}]`).forEach((column: Element): void => {
    column.removeAttribute(TARGET)
  })
}

// The card the dropped one lands before: the first card whose middle lies below the pointer.
const landingBefore = (list: HTMLElement, card: HTMLElement, pointer: number): Element | null =>
  [...list.querySelectorAll('.preview-card, [data-preview-placeholder]')].find((other: Element): boolean => {
    const box: DOMRect = other.getBoundingClientRect()
    return other !== card && box.top + box.height * MIDDLE > pointer
  }) ?? null

const counted = (column: Element | null, change: number): void => {
  const count: Element | null | undefined = column?.querySelector('[data-preview-count]')
  if (count instanceof HTMLElement) {
    count.replaceChildren(String(Number(count.textContent) + change))
  }
}

const announce = (preview: HTMLElement, column: HTMLElement): void => {
  const toast: Element | null = preview.querySelector('[data-preview-toast]')
  if (toast instanceof HTMLElement) {
    toast.replaceChildren((toast.dataset['moved'] ?? '{0}').split('{0}').join(column.dataset['name'] ?? ''))
  }
}

// The floating card came out of the first column, so it counts there until it lands somewhere.
const sourceOf = (preview: HTMLElement, card: HTMLElement): Element | null =>
  card.closest('[data-preview-column]') ?? preview.querySelector('[data-preview-column]')

const land = (preview: HTMLElement, card: HTMLElement, list: HTMLElement, pointer: number): void => {
  const column: HTMLElement | undefined = within(list, '[data-preview-column]')
  const source: Element | null = sourceOf(preview, card)
  const lifted: Element | null = card.closest('[data-preview-lifted]')
  list.insertBefore(card, landingBefore(list, card, pointer))
  card.classList.remove(...LIFTED)
  card.classList.add(RESTING)
  lifted?.remove()
  preview.querySelectorAll('[data-preview-placeholder]').forEach((placeholder: Element): void => {
    placeholder.remove()
  })
  if (column === undefined || column === source) {
    return
  }

  counted(source, -1)
  counted(column, 1)
  announce(preview, column)
}

const previewOf = (target: EventTarget | null): HTMLElement | undefined => within(target, '.preview')

// A browser draws what the pointer carries from the panel the card sits in, and that panel stays put beside
// the page as it scrolls, which some browsers draw whole rather than the card alone. So the pointer carries
// a copy of the card laid over it outside the panel, gone again once the browser has drawn it.
const carry = (root: Document, card: HTMLElement, event: DragEvent): void => {
  const transfer: DataTransfer | null | undefined = event.dataTransfer
  const copy: Node = card.cloneNode(true)
  if (!(transfer && copy instanceof HTMLElement)) {
    return
  }

  const box: DOMRect = card.getBoundingClientRect()
  copy.classList.add('preview-carried')
  copy.style.setProperty('left', `${String(box.left)}px`)
  copy.style.setProperty('top', `${String(box.top)}px`)
  copy.style.setProperty('width', `${String(box.width)}px`)
  root.body.append(copy)
  transfer.setDragImage(copy, event.clientX - box.left, event.clientY - box.top)
  requestAnimationFrame((): void => {
    copy.remove()
  })
}

/**
 * Lets the cards of the preview board be dragged between its columns. The listeners sit on the document,
 * so a preview that a navigation swaps into the page later works as well as one the page opened with.
 * @param root the document the preview may be drawn in
 */
export const enablePreview = (root: Document): void => {
  root.addEventListener('dragstart', (event: DragEvent): void => {
    const card: HTMLElement | undefined = within(event.target, '.preview-card')
    if (card === undefined || previewOf(card) === undefined) {
      return
    }

    event.dataTransfer?.setData('text/plain', card.textContent.trim())
    carry(root, card, event)
    card.setAttribute(DRAGGING, '')
  })
  root.addEventListener('dragend', (event: DragEvent): void => {
    const preview: HTMLElement | undefined = previewOf(event.target)
    if (preview === undefined) {
      return
    }

    dragged(preview)?.removeAttribute(DRAGGING)
    clearTargets(preview)
  })
  root.addEventListener('dragover', (event: DragEvent): void => {
    const preview: HTMLElement | undefined = previewOf(event.target)
    const column: HTMLElement | undefined = within(event.target, '[data-preview-column]')
    if (preview === undefined || column === undefined || dragged(preview) === undefined) {
      return
    }

    event.preventDefault()
    clearTargets(preview)
    column.setAttribute(TARGET, '')
  })
  root.addEventListener('drop', (event: DragEvent): void => {
    const preview: HTMLElement | undefined = previewOf(event.target)
    if (preview === undefined) {
      return
    }
    const column: HTMLElement | undefined = within(event.target, '[data-preview-column]')
    const list: Element | null | undefined = column?.querySelector('[data-preview-list]')
    const card: HTMLElement | undefined = dragged(preview)
    if (card !== undefined && list instanceof HTMLElement) {
      event.preventDefault()
      land(preview, card, list, event.clientY)
    }
    clearTargets(preview)
  })
}
