import { describe, expect, it } from 'vitest'
import { type PageInlineStyle, type ReservableElement, reservedBoxOf } from '@/layout-reserved.js'

/* eslint-disable functional/immutable-data -- the stand-in element records the DOM writes the measurement makes, which is what the tests read */

// An element as far as the measurement sees it: its declarations, its attributes, and every attribute it
// was asked to write, because a page whose policy refuses inline style refuses a written style attribute.
class Element implements ReservableElement {
  readonly written: string[] = []
  readonly attributes: Map<string, string> = new Map<string, string>()
  readonly declarations: Map<string, readonly [string, string]> = new Map<string, readonly [string, string]>()
  readonly style: PageInlineStyle = {
    getPropertyValue: (property: string): string => this.declarations.get(property)?.[0] ?? '',
    getPropertyPriority: (property: string): string => this.declarations.get(property)?.[1] ?? '',
    setProperty: (property: string, value: string, priority: string): void => {
      this.declarations.set(property, [value, priority])
      this.attributes.set('style', this.attributes.get('style') ?? '')
    },
    removeProperty: (property: string): string => {
      this.declarations.delete(property)
      return ''
    },
  }

  constructor(style: string | null) {
    if (style !== null) {
      this.attributes.set('style', style)
    }
  }

  getAttribute(name: string): string | null {
    return this.attributes.get(name) ?? null
  }

  cloneNode(): ReservableElement {
    return new Element(null)
  }

  // Removing the attribute removes its declarations, as a document does.
  removeAttribute(name: string): void {
    this.attributes.delete(name)
    if (name === 'style') {
      this.declarations.clear()
    }
  }

  setAttribute(name: string, value: string): void {
    this.written.push(name)
    this.attributes.set(name, value)
  }

  before(): void {
    this.written.push('before')
  }

  remove(): void {
    this.written.push('remove')
  }

  getBoundingClientRect(): { readonly width: number; readonly height: number } {
    return { width: 48, height: 27 }
  }
}
/* eslint-enable functional/immutable-data -- the tests below only read what the stand-in recorded */

describe('the box a replaced element reserves', () => {
  it('measures the placeholder and leaves an element without a style attribute without one', () => {
    const element: Element = new Element(null)
    expect(reservedBoxOf(element)).toEqual({ width: 48, height: 27 })
    expect(element.getAttribute('style')).toBeNull()
    expect(element.declarations.size).toBe(0)
  })

  it('restores an empty style attribute through the object model rather than writing it', () => {
    const element: Element = new Element('')
    reservedBoxOf(element)
    expect(element.getAttribute('style')).toBe('')
    expect(element.declarations.size).toBe(0)
    expect(element.written).not.toContain('style')
  })

  it('gives back a display the element declared, with its priority', () => {
    const element: Element = new Element('display: flex !important')
    element.style.setProperty('display', 'flex', 'important')
    reservedBoxOf(element)
    expect(element.declarations.get('display')).toEqual(['flex', 'important'])
    expect(element.written).not.toContain('style')
  })
})
