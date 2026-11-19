// A date field is a typed line the form posts and a calendar in a disclosure beside it. The page draws a
// placeholder carrying what the calendar starts on, and the calendar, a web component, is built inside it
// here, after which the disclosure is shown; a page without this script keeps the typed line alone. The
// component's change event does not bubble, so the document listens in the capture phase, which reaches
// every element on the way down whether its events bubble or not. A day picked in the calendar becomes the
// line's value and closes the calendar; a day typed into the line becomes the day the calendar shows.

const FIELD: string = '[data-date-field]'

const CALENDAR: string = `${FIELD} calendar-date`

const LINE: string = `${FIELD} input`

const ISO_DAY: RegExp = /^\d{4}-\d{2}-\d{2}$/u

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
  calendar.append(...host.children, document.createElement('calendar-month'))
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
  if (target instanceof HTMLInputElement && target.matches(LINE) && ISO_DAY.test(target.value)) {
    fieldOf(target)?.calendar.setAttribute('value', target.value)
  }
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
 * Wires every date field of the document, now and after any swap: its calendar is built, a picked day fills
 * the line, a typed day moves the calendar, and Escape closes an open calendar and returns focus to the button
 * that opened it.
 * @param root the document the fields are drawn in
 */
export const enableDateFields = (root: Document): void => {
  mountWithin(root)
  root.addEventListener('htmx:load', swapped)
  root.addEventListener('change', picked, { capture: true })
  root.addEventListener('input', typed)
  root.addEventListener('keydown', escaped)
}
