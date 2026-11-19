// A date field is a typed line the form posts and a calendar in a disclosure beside it. The page draws a
// placeholder carrying what the calendar starts on, and the calendar, a web component, is built inside it
// here, after which the disclosure is shown; a page without this script keeps the typed line alone. The
// component's change event does not bubble, so the document listens in the capture phase, which reaches
// every element on the way down whether its events bubble or not. A day picked in the calendar becomes the
// line's value and closes the calendar; a day typed into the line becomes the day the calendar shows. A click
// anywhere outside an open calendar closes it, as Escape does.

const FIELD: string = '[data-date-field]'

const CALENDAR: string = `${FIELD} calendar-date`

const LINE: string = `${FIELD} input`

const ISO_DAY: RegExp = /^\d{4}-\d{2}-\d{2}$/u

const ISO_LENGTH: number = 10

interface DateField {
  readonly line: HTMLInputElement
  readonly disclosure: HTMLDetailsElement
  readonly calendar: HTMLElement
}

const fieldOf = (target: EventTarget | null): DateField | undefined => {
  const field: Element | null = target instanceof Element ? target.closest(FIELD) : null
  const line: Element | null | undefined = field?.querySelector('input')
  const disclosure: Element | null | undefined = field?.querySelector('details')
  const calendar: Element | null | undefined = field?.querySelector('calendar-date')
  return line instanceof HTMLInputElement && disclosure instanceof HTMLDetailsElement && calendar instanceof HTMLElement
    ? { line, disclosure, calendar }
    : undefined
}

const HOST: string = '[data-calendar]'

// What the placeholder carries, under its data name, and the attribute of the calendar each one becomes. A value
// the placeholder does not carry is left to the calendar's own default, since the calendar refuses an empty one.
const STARTS: readonly (readonly [string, string])[] = [
  ['locale', 'locale'],
  ['firstDay', 'first-day-of-week'],
  ['value', 'value'],
]

// The calendar's own header names the year alone and its month grid names the month alone, so the header is
// given one title naming both, in the page's language, which follows the month the calendar shows. The calendar
// announces every month it turns to with the day it moves to. The month grid's own name stays, hidden, as the
// name of its table.
const titleOf = (day: string, locale: string | undefined): string => {
  const [year = 0, month = 1]: readonly number[] = day.split('-').map(Number)
  const named: string = new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
    new Date(Date.UTC(year, month - 1, 1)),
  )
  return named.charAt(0).toLocaleUpperCase(locale) + named.slice(1)
}

// The calendar announces the day it moves to as a moment at midnight in UTC, whose first ten characters, written
// out, are that day.
const dayOf = (detail: unknown): string | undefined => {
  const written: string = detail instanceof Date ? detail.toISOString() : String(detail)
  const day: string = written.slice(0, ISO_LENGTH)
  return ISO_DAY.test(day) ? day : undefined
}

const today = (): string => new Date().toISOString().slice(0, ISO_LENGTH)

const titled = (calendar: HTMLElement, locale: string | undefined, start: string): void => {
  const title: HTMLElement = document.createElement('span')
  title.setAttribute('slot', 'heading')
  title.setAttribute('aria-hidden', 'true')
  title.replaceChildren(titleOf(start, locale))
  calendar.addEventListener('focusday', (event: Event): void => {
    // The calendar declares nothing about what it announces, so the announcement is read as anything at all.
    const announced: { readonly detail: unknown } = event instanceof CustomEvent ? event : { detail: undefined }
    const day: string | undefined = dayOf(announced.detail)
    if (day !== undefined) {
      title.replaceChildren(titleOf(day, locale))
    }
  })
  calendar.append(title)
}

// The month buttons the placeholder holds move into the calendar, which places them in its header by the
// slot each names.
const mount = (host: HTMLElement): void => {
  const calendar: HTMLElement = document.createElement('calendar-date')
  calendar.classList.add('cally')
  for (const [held, attribute] of STARTS) {
    const value: string | undefined = host.dataset[held]
    if (value !== undefined) {
      calendar.setAttribute(attribute, value)
    }
  }
  const value: string = host.dataset['value'] ?? ''
  calendar.append(...host.children, document.createElement('calendar-month'))
  titled(calendar, host.dataset['locale'], ISO_DAY.test(value) ? value : today())
  host.append(calendar)
  host.closest('details')?.removeAttribute('hidden')
}

const mountWithin = (root: ParentNode): void => {
  root.querySelectorAll(HOST).forEach((host: Element): void => {
    if (host instanceof HTMLElement && host.querySelector('calendar-date') === null) {
      mount(host)
    }
  })
}

const swapped = (event: Event): void => {
  if (event.target instanceof Element) {
    mountWithin(event.target)
  }
}

const close = (field: DateField): void => {
  field.disclosure.removeAttribute('open')
}

const fill = (field: DateField): void => {
  // The component keeps the picked day as a property it does not reflect to an attribute.
  const day: string = String(Reflect.get(field.calendar, 'value'))
  // eslint-disable-next-line functional/immutable-data -- an input's current value is a property no attribute sets
  Object.assign(field.line, { value: day })
  field.line.dispatchEvent(new Event('change', { bubbles: true }))
  close(field)
  field.line.focus()
}

const picked = (event: Event): void => {
  const target: EventTarget | null = event.target
  const field: DateField | undefined =
    target instanceof Element && target.matches(CALENDAR) ? fieldOf(target) : undefined
  if (field === undefined) {
    return
  }
  fill(field)
}

const typed = (event: Event): void => {
  const target: EventTarget | null = event.target
  if (!(target instanceof HTMLInputElement && target.matches(LINE) && ISO_DAY.test(target.value))) {
    return
  }
  const calendar: HTMLElement | undefined = fieldOf(target)?.calendar
  calendar?.setAttribute('value', target.value)
  // The month shown follows the focused day, which no longer follows the value once a reader has turned a month.
  calendar?.setAttribute('focused-date', target.value)
}

const dismissed = (event: Event): void => {
  const target: EventTarget | null = event.target
  document.querySelectorAll(`${FIELD} details[open]`).forEach((disclosure: Element): void => {
    if (!(target instanceof Node && disclosure.contains(target))) {
      disclosure.removeAttribute('open')
    }
  })
}

const escaped = (event: KeyboardEvent): void => {
  const field: DateField | undefined = fieldOf(event.target)
  if (event.key !== 'Escape' || field?.disclosure.open !== true) {
    return
  }
  event.preventDefault()
  close(field)
  field.disclosure.querySelector('summary')?.focus()
}

/**
 * Wires every date field of the document, now and after any swap: its calendar is built with a title naming the
 * month it shows, a picked day fills the line, a typed day moves the calendar, Escape closes an open calendar and
 * returns focus to the button that opened it, and a click outside an open calendar closes it.
 * @param root the document the fields are drawn in
 */
export const enableDateFields = (root: Document): void => {
  mountWithin(root)
  root.addEventListener('htmx:load', swapped)
  root.addEventListener('change', picked, { capture: true })
  root.addEventListener('input', typed)
  root.addEventListener('keydown', escaped)
  root.addEventListener('click', dismissed)
}
