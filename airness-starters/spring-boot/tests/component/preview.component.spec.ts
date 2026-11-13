import { afterEach, describe, expect, it } from 'vitest'
import { enablePreview } from '@/preview'

// The DOM emulator has no drag events, so the preview is driven with mouse events of the same names, which
// carry what the script reads: where they land and the pointer's height.
const drag = (type: string, target: Element, clientY: number = 0): MouseEvent => {
  const event: MouseEvent = new MouseEvent(type, { bubbles: true, cancelable: true, clientY })
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

const PREVIEW: string = `
  <div class="preview">
    <div data-preview-column data-name="Open">
      <span data-preview-count>18</span>
      <div data-preview-list>
        <div class="preview-card" id="first">First</div>
        <div class="preview-card" id="second">Second</div>
        <div data-preview-placeholder></div>
      </div>
    </div>
    <div data-preview-column data-name="Done">
      <span data-preview-count>9</span>
      <div data-preview-list><div class="preview-card" id="done">Done</div></div>
    </div>
    <div data-preview-lifted><div class="preview-card -rotate-2 shadow-lift" id="lifted">Lifted</div></div>
    <span data-preview-toast data-moved="Moved to {0}">Moved to In progress</span>
    <p>Ground</p>
  </div>
  <p id="outside">Outside</p>`

const counts = (): readonly string[] =>
  [...document.querySelectorAll('[data-preview-count]')].map((count: Element): string => count.textContent)

enablePreview(document)

describe('the preview board', (): void => {
  afterEach((): void => {
    document.body.replaceChildren()
  })

  it('moves a card to the column it is dropped on and says where it went', (): void => {
    document.body.insertAdjacentHTML('beforeend', PREVIEW)
    drag('dragstart', element('#first'))
    expect(drag('dragover', element('[data-name="Done"] .preview-card')).defaultPrevented).toBe(true)
    expect(element('[data-name="Done"]').matches('[data-target]')).toBe(true)
    drag('drop', element('[data-name="Done"] [data-preview-list]'), 1)
    drag('dragend', element('#first'))
    expect(element('#first').closest('[data-preview-column]')).toBe(element('[data-name="Done"]'))
    expect(counts()).toEqual(['17', '10'])
    expect(element('[data-preview-toast]').textContent).toBe('Moved to Done')
    expect(document.querySelectorAll('[data-preview-placeholder], [data-dragging], [data-target]')).toHaveLength(0)
  })

  it('lands the lifted card in its column as a resting one', (): void => {
    document.body.insertAdjacentHTML('beforeend', PREVIEW)
    drag('dragstart', element('#lifted'))
    drag('drop', element('[data-name="Done"]'), -1)
    expect(element('#lifted').className).toBe('preview-card press')
    expect(element('[data-name="Done"] [data-preview-list]').firstElementChild?.id).toBe('lifted')
    expect(document.querySelector('[data-preview-lifted]')).toBeNull()
    expect(counts()).toEqual(['17', '10'])
  })

  it('reorders a card within its own column without counting it twice', (): void => {
    document.body.insertAdjacentHTML('beforeend', PREVIEW)
    drag('dragstart', element('#second'))
    drag('drop', element('[data-name="Open"]'), -1)
    expect(element('[data-name="Open"] [data-preview-list]').firstElementChild?.id).toBe('second')
    expect(counts()).toEqual(['18', '9'])
    expect(element('[data-preview-toast]').textContent).toBe('Moved to In progress')
  })

  it('leaves the board as it was for a drop beside every column', (): void => {
    document.body.insertAdjacentHTML('beforeend', PREVIEW)
    drag('dragstart', element('#first'))
    expect(drag('dragover', element('.preview p')).defaultPrevented).toBe(false)
    drag('drop', element('.preview p'))
    expect(counts()).toEqual(['18', '9'])
  })

  it('takes no card when nothing is being dragged', (): void => {
    document.body.insertAdjacentHTML('beforeend', PREVIEW)
    expect(drag('dragover', element('[data-name="Done"]')).defaultPrevented).toBe(false)
    drag('drop', element('[data-name="Done"]'))
    expect(counts()).toEqual(['18', '9'])
  })

  it('ignores a drag that starts or ends outside the preview', (): void => {
    document.body.insertAdjacentHTML('beforeend', PREVIEW)
    drag('dragstart', element('#outside'))
    drag('dragend', element('#outside'))
    drag('drop', element('#outside'))
    expect(document.querySelector('[data-dragging]')).toBeNull()
  })
})
