import { afterEach, describe, expect, it } from 'vitest'
import { openNamedDialog } from '@/dialogs'

const element = <T extends Element>(selector: string, type: abstract new () => T): T => {
  const found: Element | null = document.querySelector(selector)
  if (!(found instanceof type)) {
    throw new TypeError(`No ${type.name} matches ${selector}`)
  }
  return found
}

describe('a control that names a dialog', (): void => {
  afterEach((): void => {
    document.body.replaceChildren()
  })

  it('opens the dialog it names as a modal', (): void => {
    document.body.insertAdjacentHTML(
      'beforeend',
      '<button type="button" data-opens="confirm"><span>Delete</span></button><dialog id="confirm"></dialog>',
    )
    document.body.addEventListener('click', openNamedDialog)
    element('span', HTMLSpanElement).click()
    expect(element('dialog', HTMLDialogElement).open).toBe(true)
  })

  it('opens nothing for a control that names no dialog', (): void => {
    document.body.insertAdjacentHTML('beforeend', '<button type="button" data-opens="missing">Delete</button>')
    document.body.addEventListener('click', openNamedDialog)
    element('button', HTMLButtonElement).click()
    expect(document.querySelector('dialog')).toBeNull()
  })

  it('opens nothing for a click outside every control that names a dialog', (): void => {
    document.body.insertAdjacentHTML('beforeend', '<p>Text</p><dialog id="confirm"></dialog>')
    document.body.addEventListener('click', openNamedDialog)
    element('p', HTMLParagraphElement).click()
    expect(element('dialog', HTMLDialogElement).open).toBe(false)
  })

  it('ignores a click that landed on no element', (): void => {
    expect((): void => {
      openNamedDialog(new Event('click'))
    }).not.toThrow()
  })
})
