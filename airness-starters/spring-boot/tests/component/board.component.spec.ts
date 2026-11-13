import { afterEach, describe, expect, it } from 'vitest'
import { enableBoard } from '@/board'

// Each form the board submits marks itself, the way a listener sees the submission the browser hands over.
const recordSubmissions = (): void => {
  document.querySelectorAll('form').forEach((form: HTMLFormElement): void => {
    form.addEventListener('submit', (event: SubmitEvent): void => {
      event.preventDefault()
      form.classList.add('submitted')
    })
  })
}

const submissions = (): number => document.querySelectorAll('form.submitted').length

// The DOM emulator has no drag events, so the board is driven with mouse events of the same names, which
// carry what the script reads: where they land and the pointer's height.
const drag = (type: string, target: Element): MouseEvent => {
  const event: MouseEvent = new MouseEvent(type, { bubbles: true, cancelable: true })
  target.dispatchEvent(event)
  return event
}

const element = (selector: string): HTMLElement => {
  const found: Element | null = document.querySelector(selector)
  if (!(found instanceof HTMLElement)) {
    throw new TypeError(`No element matches ${selector}`)
  }
  return found
}

const BOARD: string = `
  <section class="board-column" data-status="OPEN">
    <ul><li class="board-card" data-reference="first"><span>Card</span><form data-move-to="DONE"></form></li></ul>
  </section>
  <section class="board-column" data-status="DONE"><ul><li>Empty</li></ul></section>
  <p>Outside</p>`

enableBoard(document)

describe('the board', (): void => {
  afterEach((): void => {
    document.body.replaceChildren()
  })

  it('submits the move a card offers for the column it is dropped on', (): void => {
    document.body.insertAdjacentHTML('beforeend', BOARD)
    recordSubmissions()
    drag('dragstart', element('.board-card span'))
    expect(element('.board-card').matches('[data-dragging]')).toBe(true)
    expect(drag('dragover', element('[data-status="DONE"] li')).defaultPrevented).toBe(true)
    expect(element('[data-status="DONE"]').matches('[data-target]')).toBe(true)
    drag('drop', element('[data-status="DONE"] li'))
    expect(submissions()).toBe(1)
    drag('dragend', element('.board-card'))
    expect(document.querySelector('[data-dragging], [data-target]')).toBeNull()
  })

  it('offers no landing on the column the card already stands in', (): void => {
    document.body.insertAdjacentHTML('beforeend', BOARD)
    drag('dragstart', element('.board-card'))
    expect(drag('dragover', element('[data-status="OPEN"]')).defaultPrevented).toBe(false)
  })

  it('submits nothing for a card dropped outside every column', (): void => {
    document.body.insertAdjacentHTML('beforeend', BOARD)
    recordSubmissions()
    drag('dragstart', element('.board-card'))
    drag('dragover', element('p'))
    drag('drop', element('p'))
    expect(submissions()).toBe(0)
  })

  it('picks up nothing when a drag starts outside every card', (): void => {
    document.body.insertAdjacentHTML('beforeend', BOARD)
    drag('dragstart', element('p'))
    expect(document.querySelector('[data-dragging]')).toBeNull()
  })

  it('ignores drag events that landed on no element', (): void => {
    expect((): void => {
      document.dispatchEvent(new MouseEvent('dragstart', { bubbles: true }))
      document.dispatchEvent(new MouseEvent('drop', { bubbles: true }))
    }).not.toThrow()
  })
})
