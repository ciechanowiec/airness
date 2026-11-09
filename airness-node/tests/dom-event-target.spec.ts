import { Window } from 'happy-dom'
import { describe, expect, it } from 'vitest'
import { unifyEventTarget } from '@/dom-event-target.js'

// Each spec gets a window of its own, so a repaired EventTarget never reaches the next.
const inWindow = async (use: (window: Window) => void): Promise<void> => {
  const window: Window = new Window()
  try {
    use(window)
  } finally {
    await window.happyDOM.close()
  }
}

describe('unifyEventTarget', () => {
  it('makes the nodes of a happy-dom window instances of its EventTarget, as in a browser', async () => {
    await inWindow((window: Window): void => {
      const node: unknown = window.document.createElement('div')
      expect(node).not.toBeInstanceOf(window.EventTarget)

      unifyEventTarget(window)
      unifyEventTarget(window)

      expect(node).toBeInstanceOf(window.EventTarget)
      expect(window.document).toBeInstanceOf(window.EventTarget)
      expect(new window.EventTarget()).toBeInstanceOf(window.EventTarget)
      expect({}).not.toBeInstanceOf(window.EventTarget)
    })
  })

  it('leaves a scope alone that has no document, or whose document the parent class does not hold', async () => {
    await inWindow((window: Window): void => {
      const node: unknown = window.document.createElement('div')

      unifyEventTarget({ EventTarget: window.EventTarget })
      unifyEventTarget({ EventTarget: window.EventTarget, document: {} })
      unifyEventTarget({ EventTarget: 'not a class', document: window.document })

      expect(node).not.toBeInstanceOf(window.EventTarget)
    })
  })
})
