// The visual fixture's pages that only a scrolled page shows to be wrong: one that takes the scroll over
// once the reader stops, and one whose text waits for an observer that never reports it. Each page renders
// the same markup in both modes; the defect is only in how it answers the scroll.

/** A fixture page's body with its defect, and the same markup repaired. */
interface ScrollVariant {
  readonly defect: string
  readonly valid: string
}

/**
 * Sections a phone's viewport tall. When `snaps` is set, the document is moved to the nearest section a
 * moment after the scroll events stop, as a full-page section library does. Otherwise the page scrolls
 * natively and pins a band while the sections pass, which moves nothing and must pass.
 */
const SNAP: string = [
  "'use client'",
  "import { useEffect } from 'react'",
  '',
  "const SECTIONS: readonly string[] = ['First', 'Second', 'Third', 'Fourth']",
  'const SNAP_DELAY_MS = 150',
  '',
  'export function Snap({ snaps }: { snaps: boolean }): React.JSX.Element {',
  '  useEffect((): (() => void) | undefined => {',
  '    if (!snaps) {',
  '      return undefined',
  '    }',
  '    let timer: number | undefined',
  '    const snap = (): void => {',
  '      window.clearTimeout(timer)',
  '      timer = window.setTimeout((): void => {',
  '        const height: number = window.innerHeight',
  "        window.scrollTo({ top: Math.round(window.scrollY / height) * height, behavior: 'instant' })",
  '      }, SNAP_DELAY_MS)',
  '    }',
  "    window.addEventListener('scroll', snap, { passive: true })",
  '    return (): void => {',
  '      window.clearTimeout(timer)',
  "      window.removeEventListener('scroll', snap)",
  '    }',
  '  }, [snaps])',
  '  return (',
  '    <div className="snap-track">',
  '      <div className="snap-band" aria-hidden="true" />',
  '      {SECTIONS.map((name: string): React.JSX.Element => (',
  '        <section key={name} className="snap-section">',
  "          <h2>{name + ' section'}</h2>",
  '          <p>Each section is as tall as the viewport.</p>',
  '        </section>',
  '      ))}',
  '    </div>',
  '  )',
  '}',
  '',
].join('\n')

/**
 * A panel taller than a phone's viewport, faded in once an observer reports the given share of it in view.
 * Asking for nine tenths of a panel that can never be nine tenths in view leaves it transparent for good;
 * asking for any of it reveals it as it arrives.
 */
const REVEAL: string = [
  "'use client'",
  "import { useEffect, useRef, useState } from 'react'",
  '',
  'export function Reveal({ threshold }: { threshold: number }): React.JSX.Element {',
  '  const panel = useRef<HTMLDivElement>(null)',
  '  const [isShown, setShown] = useState(false)',
  '  useEffect((): (() => void) | undefined => {',
  '    const element: HTMLDivElement | null = panel.current',
  '    if (element === null) {',
  '      return undefined',
  '    }',
  '    const isArrived = (entry: IntersectionObserverEntry): boolean =>',
  '      entry.isIntersecting && entry.intersectionRatio >= threshold',
  '    const observer: IntersectionObserver = new IntersectionObserver(',
  '      (entries: IntersectionObserverEntry[]): void => {',
  '        if (entries.some(isArrived)) {',
  '          setShown(true)',
  '          observer.disconnect()',
  '        }',
  '      },',
  '      { threshold },',
  '    )',
  '    observer.observe(element)',
  '    return (): void => observer.disconnect()',
  '  }, [threshold])',
  '  return (',
  '    <>',
  '      <p className="reveal-lead">The panel below fades in as it arrives.</p>',
  "      <div ref={panel} className={isShown ? 'reveal-panel shown' : 'reveal-panel'}>",
  '        <h2>Revealed panel</h2>',
  '        <p>Text a reader scrolled here to read.</p>',
  '      </div>',
  '    </>',
  '  )',
  '}',
  '',
].join('\n')

/** The client components these pages render, by the name a page imports each under. */
export const SCROLL_COMPONENTS: Readonly<Record<string, string>> = { Reveal: REVEAL, Snap: SNAP }

/** Each page's body in both modes, by its route. */
export const SCROLL_PAGES: Readonly<Record<string, ScrollVariant>> = {
  'scroll-hijack': { defect: '<Snap snaps={true} />', valid: '<Snap snaps={false} />' },
  'unrevealed-content': { defect: '<Reveal threshold={0.9} />', valid: '<Reveal threshold={0} />' },
}

/** The styles of these pages. */
export const SCROLL_STYLES: string = `
.snap-track { position: relative; }
.snap-band { position: sticky; top: 0; height: 8px; background: #1d4ed8; }
.snap-section { min-height: 100svh; }
.reveal-lead { min-height: 120svh; }
.reveal-panel { min-height: 250svh; opacity: 0; transition: opacity 300ms; }
.reveal-panel.shown { opacity: 1; }
`
