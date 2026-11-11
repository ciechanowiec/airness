// The client components of the visual fixture that the keyboard rules of the WAI-ARIA patterns judge: a
// dialog and a tab list, each in a defective and a repaired form.

/**
 * A modal dialog by role that moves focus in when it opens. Unrepaired, it neither traps Tab nor answers
 * Escape; its opener still toggles it, which is how the sweep closes it again. Repaired, it does both, beside
 * a native modal dialog and an alert dialog an invoker command opens, which the browser keeps right itself.
 */
export const DIALOGS: string = [
  "'use client'",
  "import { type KeyboardEvent, useEffect, useRef, useState } from 'react'",
  '',
  'export function Dialogs({ isRepaired }: { isRepaired: boolean }): React.JSX.Element {',
  '  const [isOpen, setOpen] = useState(false)',
  '  const opener = useRef<HTMLButtonElement>(null)',
  '  const first = useRef<HTMLButtonElement>(null)',
  '  const last = useRef<HTMLButtonElement>(null)',
  '  const native = useRef<HTMLDialogElement>(null)',
  '  const invoker = useRef<HTMLButtonElement>(null)',
  '  useEffect((): void => {',
  '    if (isOpen) {',
  '      first.current?.focus()',
  '    }',
  '  }, [isOpen])',
  '  useEffect((): void => {',
  "    invoker.current?.setAttribute('commandfor', 'help-dialog')",
  "    invoker.current?.setAttribute('command', 'show-modal')",
  '  }, [])',
  '  const close = (): void => {',
  '    setOpen(false)',
  '    opener.current?.focus()',
  '  }',
  '  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>): void => {',
  "    if (event.key === 'Escape') {",
  '      close()',
  "    } else if (event.key === 'Tab') {",
  '      const edge = event.shiftKey ? first.current : last.current',
  '      if (document.activeElement === edge) {',
  '        event.preventDefault()',
  '        ;(event.shiftKey ? last.current : first.current)?.focus()',
  '      }',
  '    }',
  '  }',
  '  return (',
  '    <div>',
  '      <button ref={opener} type="button" aria-haspopup="dialog" aria-expanded={isOpen}',
  '        onClick={(): void => setOpen(!isOpen)}>',
  '        Open settings',
  '      </button>',
  '      {isOpen ? (',
  '        <div role="dialog" aria-modal="true" aria-label="Settings" className="settings"',
  '          onKeyDown={isRepaired ? onKeyDown : undefined}>',
  '          <button ref={first} type="button">Save</button>',
  '          <button ref={last} type="button" onClick={close}>Close</button>',
  '        </div>',
  '      ) : null}',
  '      {isRepaired ? (',
  '        <>',
  '          <button type="button" aria-haspopup="dialog" onClick={(): void => native.current?.showModal()}>',
  '            Open details',
  '          </button>',
  '          <dialog ref={native} aria-label="Details">',
  '            <button type="button" onClick={(): void => native.current?.close()}>Close details</button>',
  '          </dialog>',
  '          <button ref={invoker} type="button">Open help</button>',
  '          <dialog id="help-dialog" role="alertdialog" aria-label="Help">',
  '            <button type="button">Got it</button>',
  '          </dialog>',
  '        </>',
  '      ) : null}',
  '      <a href="/">After the dialogs</a>',
  '    </div>',
  '  )',
  '}',
  '',
].join('\n')

/** A tab list of buttons: unrepaired, each is a Tab stop and no arrow key moves among them. */
export const TABS: string = [
  "'use client'",
  "import { type KeyboardEvent, useRef, useState } from 'react'",
  '',
  "const LABELS: readonly string[] = ['Overview', 'Results', 'Fixtures']",
  '',
  'export function Tabs({ isRoving }: { isRoving: boolean }): React.JSX.Element {',
  '  const [selected, setSelected] = useState(0)',
  '  const tabs = useRef<(HTMLButtonElement | null)[]>([])',
  '  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>): void => {',
  "    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0",
  '    if (step !== 0) {',
  '      const next = (selected + step + LABELS.length) % LABELS.length',
  '      setSelected(next)',
  '      tabs.current[next]?.focus()',
  '    }',
  '  }',
  '  return (',
  '    <div>',
  '      <div role="tablist" aria-label="Fixture tabs" className="tabs" onKeyDown={isRoving ? onKeyDown : undefined}>',
  '        {LABELS.map((label: string, index: number) => (',
  '          <button key={label} ref={(element) => { tabs.current[index] = element }} type="button" role="tab"',
  '            id={"tab-" + String(index)} aria-selected={index === selected} aria-controls={"panel-" + String(index)}',
  '            tabIndex={isRoving && index !== selected ? -1 : 0} onClick={(): void => setSelected(index)}>',
  '            {label}',
  '          </button>',
  '        ))}',
  '      </div>',
  '      {LABELS.map((label: string, index: number) => (',
  '        <div key={label} role="tabpanel" id={"panel-" + String(index)} aria-labelledby={"tab-" + String(index)}',
  '          hidden={index !== selected}>',
  '          <p>{label} panel</p>',
  '        </div>',
  '      ))}',
  '    </div>',
  '  )',
  '}',
  '',
].join('\n')

/** The client components above, by the name a page renders them under. */
export const ARIA_COMPONENTS: Readonly<Record<string, string>> = { Dialogs: DIALOGS, Tabs: TABS }

/** A page's body in each mode. */
interface AriaVariant {
  readonly defect: string
  readonly valid: string
}

/** Each WAI-ARIA rule's page, in both modes, by its route. */
export const ARIA_PAGES: Readonly<Record<string, AriaVariant>> = {
  // A modal dialog by role that lets Tab out and ignores Escape. The repair traps Tab and closes on Escape,
  // beside a native modal dialog and one an invoker command opens, which the browser keeps right itself.
  'dialog-keyboard': { defect: '<Dialogs isRepaired={false} />', valid: '<Dialogs isRepaired={true} />' },
  // A tab list whose every tab is a Tab stop and whose arrow keys do nothing; the repair roves tabindex.
  'composite-keyboard': { defect: '<Tabs isRoving={false} />', valid: '<Tabs isRoving={true} />' },
  'aria-state': {
    defect:
      '<button type="button" aria-expanded="true" aria-controls="details-panel">Details</button>' +
      '<div id="details-panel" hidden><p>More details</p></div>',
    valid:
      '<button type="button" aria-expanded="true" aria-controls="details-panel">Details</button>' +
      '<div id="details-panel"><p>More details</p></div>',
  },
  'aria-current': {
    defect:
      '<nav aria-label="Sections" className="sections"><a href="/aria-current">This page</a><a href="/">Home</a></nav>',
    valid:
      '<nav aria-label="Sections" className="sections"><a href="/aria-current" aria-current="page">This page</a>' +
      '<a href="/">Home</a></nav>',
  },
}

/** The layout the tab list and the navigation of these pages share with the settings disclosure. */
export const ARIA_STYLES: string = `
.tabs, .sections { display: flex; flex-wrap: wrap; gap: 16px; }
.tabs button, .settings button { margin: 0; }
.tabs { margin-bottom: 16px; }
`
