// The board lets a card be dragged onto another column. Dropping it submits the move form the card already
// carries for that column's status, so a drag is the same request as choosing the move from the card's menu,
// and the board works by keyboard and without script through that menu alone.

const DRAGGING: string = 'data-dragging'

const TARGET: string = 'data-target'

const cardOf = (target: EventTarget | null): HTMLElement | undefined => {
  const card: Element | null = target instanceof Element ? target.closest('.board-card') : null
  return card instanceof HTMLElement ? card : undefined
}

const columnOf = (target: EventTarget | null): HTMLElement | undefined => {
  const column: Element | null = target instanceof Element ? target.closest('.board-column') : null
  return column instanceof HTMLElement ? column : undefined
}

const dragged = (): HTMLElement | undefined => {
  const card: Element | null = document.querySelector(`[${CSS.escape(DRAGGING)}]`)
  return card instanceof HTMLElement ? card : undefined
}

// The move form of the dragged card for the column under the pointer, which is absent over the column the
// card already stands in, since a card offers no move to its own status.
const moveFor = (column: HTMLElement): HTMLFormElement | undefined => {
  const status: string = column.dataset['status'] ?? ''
  const form: Element | null | undefined = dragged()?.querySelector(`form[data-move-to="${CSS.escape(status)}"]`)
  return form instanceof HTMLFormElement ? form : undefined
}

const clearTargets = (): void => {
  document.querySelectorAll(`[${CSS.escape(TARGET)}]`).forEach((column: Element): void => {
    column.removeAttribute(TARGET)
  })
}

/**
 * Lets the cards of a board be dragged between its columns.
 * @param root the document the board is drawn in
 */
export const enableBoard = (root: Document): void => {
  root.addEventListener('dragstart', (event: DragEvent): void => {
    const card: HTMLElement | undefined = cardOf(event.target)
    if (card === undefined) {
      return
    }

    card.setAttribute(DRAGGING, '')
    event.dataTransfer?.setData('text/plain', card.dataset['reference'] ?? '')
  })
  root.addEventListener('dragend', (): void => {
    dragged()?.removeAttribute(DRAGGING)
    clearTargets()
  })
  root.addEventListener('dragover', (event: DragEvent): void => {
    const column: HTMLElement | undefined = columnOf(event.target)
    if (column === undefined || moveFor(column) === undefined) {
      return
    }

    event.preventDefault()
    clearTargets()
    column.setAttribute(TARGET, '')
  })
  root.addEventListener('drop', (event: DragEvent): void => {
    const column: HTMLElement | undefined = columnOf(event.target)
    const form: HTMLFormElement | undefined = column === undefined ? undefined : moveFor(column)
    if (form !== undefined) {
      event.preventDefault()
      form.requestSubmit()
    }
    clearTargets()
  })
}
