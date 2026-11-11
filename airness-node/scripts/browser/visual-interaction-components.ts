// The visual fixture's pages that only driving the page shows to be wrong: an element only a pointer can
// operate, a page that changes context while it loads, sign-in fields that refuse a paste, and content that
// moves under reduced motion when the reader scrolls or opens something. Each page renders the same markup in
// both modes; the defect is only in how it answers.

/** A fixture page's body with its defect, and the same markup repaired. */
interface InteractionVariant {
  readonly defect: string
  readonly valid: string
}

/**
 * Three things a pointer is invited to operate, each in the spellings that leave it out of the keyboard's
 * reach - a `div` with a pointer cursor and a click handler, a `role="button"` without a tab stop, an inline
 * `onclick` - or repaired as native buttons. Around them, what must pass in both modes: a clickable card whose
 * title is a real link, a radio group whose unselected radio, which Tab skips, shows a pointer, and a label
 * that shows one for its checkbox.
 */
const CLICKABLE: string = [
  "'use client'",
  "import { useState } from 'react'",
  '',
  'export function Clickable({ keyboard }: { keyboard: boolean }): React.JSX.Element {',
  '  const [count, setCount] = useState(0)',
  '  const press = (): void => setCount(count + 1)',
  '  return (',
  '    <div className="clickables">',
  '      <p>Pressed {count} times.</p>',
  '      {keyboard ? (',
  '        <>',
  '          <button type="button" className="clickable-card" onClick={press}>Open details</button>',
  '          <button type="button" className="role-action" onClick={press}>Role action</button>',
  '          <button type="button" className="inline-action" onClick={press}>Inline action</button>',
  '        </>',
  '      ) : (',
  '        <>',
  '          <div className="clickable-card" onClick={press}>Open details</div>',
  '          <div className="role-action" role="button" onClick={press}>Role action</div>',
  '          <div dangerouslySetInnerHTML={{',
  '            __html: \'<span class="inline-action" onclick="void 0">Inline action</span>\',',
  '          }} />',
  '        </>',
  '      )}',
  '      <div className="clickable-card linked-card" onClick={press}>',
  '        <a href="/">Read the story</a>',
  '      </div>',
  '      <fieldset className="choices">',
  '        <legend>Size</legend>',
  '        <label><input className="choice" type="radio" name="size" defaultChecked /> Small</label>',
  '        <label><input className="choice" type="radio" name="size" /> Large</label>',
  '        <label className="remember"><input type="checkbox" name="remember" /> Remember the size</label>',
  '      </fieldset>',
  '    </div>',
  '  )',
  '}',
  '',
].join('\n')

/** A page that takes the reader to the home page once it has mounted, as a client-side redirect does. */
const RELOCATE: string = [
  "'use client'",
  "import { useRouter } from 'next/navigation'",
  "import { useEffect } from 'react'",
  '',
  'export function Relocate({ moves }: { moves: boolean }): React.JSX.Element {',
  '  const router = useRouter()',
  '  useEffect((): void => {',
  '    if (moves) {',
  "      router.replace('/')",
  '    }',
  '  }, [moves, router])',
  '  return <p>This page stays where the reader opened it.</p>',
  '}',
  '',
].join('\n')

/** A page that opens a window of its own once it has mounted, as an interstitial or a newsletter offer does. */
const INTERSTITIAL: string = [
  "'use client'",
  "import { useEffect } from 'react'",
  '',
  'export function Interstitial({ opens }: { opens: boolean }): React.JSX.Element {',
  '  useEffect((): void => {',
  '    if (opens) {',
  "      window.open('/', '_blank')",
  '    }',
  '  }, [opens])',
  '  return <p>This page offers a newsletter only when asked.</p>',
  '}',
  '',
].join('\n')

/**
 * A sign-in form whose fields refuse a paste in three spellings - a React `onPaste`, a listener added to the
 * field, a listener on the document - and whose password field turns autofill off; repaired, it accepts both.
 */
const SIGN_IN: string = [
  "'use client'",
  "import { useEffect, useRef } from 'react'",
  '',
  'export function SignIn({ blocks }: { blocks: boolean }): React.JSX.Element {',
  '  const username = useRef<HTMLInputElement>(null)',
  '  useEffect((): (() => void) | undefined => {',
  '    const field: HTMLInputElement | null = username.current',
  '    if (!blocks || field === null) {',
  '      return undefined',
  '    }',
  '    const refuse = (event: Event): void => event.preventDefault()',
  '    const refuseCode = (event: Event): void => {',
  "      if (event.target instanceof HTMLInputElement && event.target.id === 'code') {",
  '        event.preventDefault()',
  '      }',
  '    }',
  "    field.addEventListener('paste', refuse)",
  "    document.addEventListener('paste', refuseCode)",
  '    return (): void => {',
  "      field.removeEventListener('paste', refuse)",
  "      document.removeEventListener('paste', refuseCode)",
  '    }',
  '  }, [blocks])',
  '  return (',
  '    <form className="sign-in" aria-label="Sign in">',
  '      <label htmlFor="username">Username</label>',
  '      <input ref={username} id="username" name="username" autoComplete="username" />',
  '      <label htmlFor="password">Password</label>',
  '      <input id="password" name="password" type="password"',
  "        autoComplete={blocks ? 'off' : 'current-password'}",
  '        onPaste={(event): void => {',
  '          if (blocks) {',
  '            event.preventDefault()',
  '          }',
  '        }} />',
  '      <label htmlFor="code">One-time code</label>',
  '      <input id="code" name="code" inputMode="numeric" autoComplete="one-time-code" />',
  '    </form>',
  '  )',
  '}',
  '',
].join('\n')

/**
 * Content that moves when the reader acts: a panel that slides in by a CSS animation when its disclosure opens,
 * a block that grows into place by a CSS transition and another that glides in by a Web Animation as a scroll
 * brings them into view, and a backdrop a scroll handler moves with the scroll. Repaired, each holds still when
 * the reader asks for reduced motion, and moves as before otherwise.
 */
const MOTION: string = [
  "'use client'",
  "import { useEffect, useRef, useState } from 'react'",
  '',
  'export function Motion({ respects }: { respects: boolean }): React.JSX.Element {',
  '  const [isOpen, setOpen] = useState(false)',
  '  const [isSlid, setSlid] = useState(false)',
  '  const drifting = useRef<HTMLDivElement>(null)',
  '  const slide = useRef<HTMLDivElement>(null)',
  '  const glide = useRef<HTMLDivElement>(null)',
  '  useEffect((): (() => void) | undefined => {',
  '    const backdrop: HTMLDivElement | null = drifting.current',
  '    const sliding: HTMLDivElement | null = slide.current',
  '    const gliding: HTMLDivElement | null = glide.current',
  '    if (backdrop === null || sliding === null || gliding === null) {',
  '      return undefined',
  '    }',
  "    const isStill = (): boolean => respects && window.matchMedia('(prefers-reduced-motion: reduce)').matches",
  '    const drift = (): void => {',
  "      backdrop.style.transform = isStill() ? '' : 'translateY(' + String(Math.round(window.scrollY * 0.3)) + 'px)'",
  '    }',
  '    const observer: IntersectionObserver = new IntersectionObserver((entries: IntersectionObserverEntry[]): void => {',
  '      for (const entry of entries.filter((arrived: IntersectionObserverEntry): boolean => arrived.isIntersecting)) {',
  '        observer.unobserve(entry.target)',
  '        if (entry.target === sliding) {',
  '          setSlid(true)',
  '        } else if (!isStill()) {',
  "          gliding.animate([{ transform: 'translateX(-48px)' }, { transform: 'none' }], { duration: 700 })",
  '        }',
  '      }',
  '    })',
  '    observer.observe(sliding)',
  '    observer.observe(gliding)',
  "    window.addEventListener('scroll', drift, { passive: true })",
  '    return (): void => {',
  '      observer.disconnect()',
  "      window.removeEventListener('scroll', drift)",
  '    }',
  '  }, [respects])',
  '  return (',
  "    <div className={respects ? 'motion respectful' : 'motion'}>",
  '      <button type="button" aria-expanded={isOpen} aria-controls="motion-panel" onClick={(): void => setOpen(!isOpen)}>',
  '        Show details',
  '      </button>',
  '      <div id="motion-panel" className="slide-panel" hidden={!isOpen}><p>Details that slide into place.</p></div>',
  '      <div ref={drifting} className="drifting"><p>A backdrop that drifts as the page scrolls.</p></div>',
  '      <p className="motion-filler">Text a reader scrolls past on the way down.</p>',
  "      <div ref={slide} className={isSlid ? 'reveal-slide shown' : 'reveal-slide'}><p>A block that grows into place.</p></div>",
  '      <div ref={glide} className="reveal-glide"><p>A block that glides in.</p></div>',
  '      <p className="motion-filler">The end of the page.</p>',
  '    </div>',
  '  )',
  '}',
  '',
].join('\n')

/** The client components these pages render, by the name a page imports each under. */
export const INTERACTION_COMPONENTS: Readonly<Record<string, string>> = {
  Clickable: CLICKABLE,
  Interstitial: INTERSTITIAL,
  Motion: MOTION,
  Relocate: RELOCATE,
  SignIn: SIGN_IN,
}

/** Each page's body in both modes, by its route. */
export const INTERACTION_PAGES: Readonly<Record<string, InteractionVariant>> = {
  'pointer-only': { defect: '<Clickable keyboard={false} />', valid: '<Clickable keyboard={true} />' },
  'unrequested-navigation': { defect: '<Relocate moves={true} />', valid: '<Relocate moves={false} />' },
  'unrequested-popup': { defect: '<Interstitial opens={true} />', valid: '<Interstitial opens={false} />' },
  'paste-blocked': { defect: '<SignIn blocks={true} />', valid: '<SignIn blocks={false} />' },
  'reduced-motion': { defect: '<Motion respects={false} />', valid: '<Motion respects={true} />' },
}

/** The styles of these pages. */
export const INTERACTION_STYLES: string = `
.clickables { display: flex; flex-direction: column; gap: 16px; }
.clickable-card, .role-action, .inline-action { display: block; cursor: pointer; padding: 12px; border: 1px solid #1d4ed8; }
.choices { display: flex; flex-direction: column; gap: 16px; min-inline-size: 0; border: 0; padding: 0; margin: 0; }
.choices input { display: inline-block; width: 24px; height: 24px; min-width: 0; min-height: 0; margin: 0 8px 0 0; }
.choice, .remember { cursor: pointer; }
.sign-in { display: flex; flex-direction: column; gap: 8px; max-width: 320px; }
.slide-panel { padding: 16px; background: #dbeafe; animation: slide-in 800ms ease-out; }
@keyframes slide-in { from { transform: translateY(48px); } to { transform: none; } }
.motion { display: flex; flex-direction: column; gap: 24px; }
.drifting { min-height: 200px; padding: 16px; background: #f3f4f6; }
.motion-filler { min-height: 100svh; }
.reveal-slide { padding: 16px; background: #dbeafe; transform: scale(0.9); transition: transform 700ms ease-out; }
.reveal-slide.shown { transform: none; }
.reveal-glide { padding: 16px; background: #dbeafe; }
@media (prefers-reduced-motion: reduce) {
  .respectful .slide-panel { animation: none; }
  .respectful .reveal-slide { transition: none; }
}
`
