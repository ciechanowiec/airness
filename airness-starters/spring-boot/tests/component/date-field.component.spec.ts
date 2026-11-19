import 'cally'
import { afterEach, describe, expect, it } from 'vitest'
import { enableDateFields } from '@/date-field'

// The calendar is the component the page loads, built inside the placeholder the page draws. It keeps the
// picked day as a property and fires a change event that does not bubble, which is what the field listens for.

// A field whose line holds the given text, as it does once a reader has typed it.
const fieldHolding = (typed: string): string => `
  <div data-date-field>
    <input name="dueOn" value="${typed}">
    <details open hidden>
      <summary>Pick</summary>
      <div data-calendar data-value="2100-01-01" data-locale="pl" data-first-day="1">
        <span slot="previous">Previous month</span>
      </div>
    </details>
  </div>
  <p>Outside</p>`

const FIELD: string = fieldHolding('2100-01-01')

const element = <T extends Element>(selector: string, type: abstract new () => T): T => {
  const found: Element | null = document.querySelector(selector)
  if (!(found instanceof type)) {
    throw new TypeError(`No ${type.name} matches ${selector}`)
  }
  return found
}

const line = (): HTMLInputElement => element('input', HTMLInputElement)

const disclosure = (): HTMLDetailsElement => element('details', HTMLDetailsElement)

const calendar = (): HTMLElement => element('calendar-date', HTMLElement)

const title = (): string => element('calendar-date [slot="heading"]', HTMLElement).textContent

// The calendar announces the day it moves to as a moment at midnight in UTC.
const turnTo = (detail: unknown): void => {
  calendar().dispatchEvent(new CustomEvent('focusday', { detail }))
}

// Draws the given markup the way htmx swaps it in, announcing it with the event htmx fires for new content.
const draw = (markup: string): void => {
  document.body.insertAdjacentHTML('beforeend', markup)
  document.body.dispatchEvent(new Event('htmx:load', { bubbles: true }))
}

const press = (target: Element, key: string): KeyboardEvent => {
  const event: KeyboardEvent = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true })
  target.dispatchEvent(event)
  return event
}

enableDateFields(document)

describe('a date field', (): void => {
  afterEach((): void => {
    document.body.replaceChildren()
  })

  it('builds its calendar from the placeholder and only then shows the disclosure', (): void => {
    draw(FIELD)
    expect(calendar().classList.contains('cally')).toBe(true)
    expect(calendar().getAttribute('value')).toBe('2100-01-01')
    expect(calendar().getAttribute('locale')).toBe('pl')
    expect(calendar().getAttribute('first-day-of-week')).toBe('1')
    expect(calendar().querySelector('[slot="previous"]')).not.toBeNull()
    expect(calendar().querySelector('calendar-month')).not.toBeNull()
    expect(disclosure().hidden).toBe(false)
  })

  it('titles the calendar with the month and the year it shows, in the language of the page', (): void => {
    draw(FIELD)
    expect(title()).toBe('Styczeń 2100')
    expect(element('calendar-date [slot="heading"]', HTMLElement).getAttribute('aria-hidden')).toBe('true')
  })

  it('retitles the calendar with every month it turns to and ignores an announcement of no day', (): void => {
    draw(FIELD)
    turnTo(new Date(Date.UTC(2100, 2, 5)))
    expect(title()).toBe('Marzec 2100')
    turnTo('2100-07-01T00:00:00.000Z')
    expect(title()).toBe('Lipiec 2100')
    turnTo('soon')
    calendar().dispatchEvent(new Event('focusday'))
    expect(title()).toBe('Lipiec 2100')
  })

  it('titles a calendar that starts on no day with the month of today', (): void => {
    draw('<div data-date-field><input><details hidden><div data-calendar data-locale="en"></div></details></div>')
    const now: Date = new Date()
    expect(title()).toBe(new Intl.DateTimeFormat('en', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(now))
  })

  it('builds one calendar however often the field is announced', (): void => {
    draw(FIELD)
    document.body.dispatchEvent(new Event('htmx:load', { bubbles: true }))
    expect(document.querySelectorAll('calendar-date')).toHaveLength(1)
  })

  it('builds a calendar from a placeholder that carries nothing', (): void => {
    draw('<div data-date-field><input><details hidden><div data-calendar></div></details></div>')
    expect(calendar().hasAttribute('value')).toBe(false)
    expect(calendar().hasAttribute('locale')).toBe(false)
    expect(calendar().hasAttribute('first-day-of-week')).toBe(false)
  })

  it('fills the line with the day picked in the calendar and closes the calendar', (): void => {
    draw(FIELD)
    line().addEventListener('change', (): void => {
      line().classList.add('announced')
    })
    calendar().setAttribute('value', '2100-02-14')
    calendar().dispatchEvent(new Event('change'))
    expect(line().value).toBe('2100-02-14')
    expect(line().classList.contains('announced')).toBe(true)
    expect(disclosure().open).toBe(false)
    expect(document.activeElement).toBe(line())
  })

  it('moves the calendar to a whole day typed into the line', (): void => {
    draw(fieldHolding('2100-03-09'))
    line().dispatchEvent(new Event('input', { bubbles: true }))
    expect(calendar().getAttribute('value')).toBe('2100-03-09')
    expect(calendar().getAttribute('focused-date')).toBe('2100-03-09')
  })

  it('closes an open calendar on a click outside it and keeps it open on a click inside', (): void => {
    draw(FIELD)
    calendar().click()
    expect(disclosure().open).toBe(true)
    element('p', HTMLParagraphElement).click()
    expect(disclosure().open).toBe(false)
  })

  it('closes an open calendar on a click that lands on no element', (): void => {
    draw(FIELD)
    document.dispatchEvent(new MouseEvent('click'))
    expect(disclosure().open).toBe(false)
  })

  it('leaves the calendar where it is while the typed day is incomplete', (): void => {
    draw(fieldHolding('2100-03'))
    line().dispatchEvent(new Event('input', { bubbles: true }))
    expect(calendar().getAttribute('value')).toBe('2100-01-01')
  })

  it('treats a change of the line itself as no pick', (): void => {
    draw(FIELD)
    line().dispatchEvent(new Event('change', { bubbles: true }))
    expect(disclosure().open).toBe(true)
  })

  it('closes an open calendar on Escape and returns focus to its button', (): void => {
    draw(FIELD)
    expect(press(calendar(), 'Escape').defaultPrevented).toBe(true)
    expect(disclosure().open).toBe(false)
    expect(document.activeElement).toBe(element('summary', HTMLElement))
  })

  it('leaves Escape alone while the calendar is closed and other keys alone while it is open', (): void => {
    draw(FIELD)
    expect(press(calendar(), 'Enter').defaultPrevented).toBe(false)
    disclosure().removeAttribute('open')
    expect(press(line(), 'Escape').defaultPrevented).toBe(false)
  })

  it('ignores events outside every date field', (): void => {
    draw(FIELD)
    const outside: HTMLParagraphElement = element('p', HTMLParagraphElement)
    outside.dispatchEvent(new Event('change', { bubbles: true }))
    outside.dispatchEvent(new Event('input', { bubbles: true }))
    expect(press(outside, 'Escape').defaultPrevented).toBe(false)
    expect(disclosure().open).toBe(true)
  })

  it('ignores a field that is missing its calendar', (): void => {
    draw('<div data-date-field><input value="x"></div>')
    line().dispatchEvent(new Event('change', { bubbles: true }))
    expect(line().value).toBe('x')
  })

  it('ignores a pick in a field that is missing its disclosure', (): void => {
    draw('<div data-date-field><input value="x"><div data-calendar data-value="2100-02-14"></div></div>')
    calendar().dispatchEvent(new Event('change'))
    expect(line().value).toBe('x')
  })

  it('ignores an event that landed on no element', (): void => {
    expect((): void => {
      document.dispatchEvent(new Event('input'))
      document.dispatchEvent(new Event('htmx:load'))
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
      document.dispatchEvent(new MouseEvent('click'))
    }).not.toThrow()
  })
})
