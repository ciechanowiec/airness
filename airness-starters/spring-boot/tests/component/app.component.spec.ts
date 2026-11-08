import { describe, expect, it } from 'vitest'

// htmx 2 looks for hx-on attributes through XPath, which the DOM emulator does not implement. No template
// of this application writes one, so a stand-in that finds none is what the browser would find.
interface NoNodes {
  readonly iterateNext: () => null
}
class NoMatches {
  createExpression(): { readonly evaluate: () => NoNodes } {
    return { evaluate: (): NoNodes => ({ iterateNext: (): null => null }) }
  }
}
// eslint-disable-next-line functional/immutable-data -- the DOM emulator lacks the XPath API htmx installs itself against
Object.defineProperty(globalThis, 'XPathEvaluator', { value: NoMatches, configurable: true })

describe('the page script', (): void => {
  it('keeps htmx from evaluating code and from writing styles the policy refuses', async (): Promise<void> => {
    await import('@/entries/app')
    const { default: htmx } = await import('htmx.org')
    expect(htmx.config.allowEval).toBe(false)
    expect(htmx.config.includeIndicatorStyles).toBe(false)
  })

  it('opens a dialog a control names once the page script runs', async (): Promise<void> => {
    await import('@/entries/app')
    document.body.insertAdjacentHTML(
      'beforeend',
      '<button type="button" data-opens="ask">Delete</button><dialog id="ask"></dialog>',
    )
    document.querySelector('button')?.click()
    expect(document.querySelector('dialog')?.open).toBe(true)
  })
})
