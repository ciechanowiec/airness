// The visual fixture's pages that only what a page draws, and how it draws it again, shows to be wrong: text
// wrapped line by line, controls carrying no text that blend into their ground, controls that answer a mouse
// and ignore a finger, effects computed again on every frame, and layers that hold more memory than the page
// shows. Each page renders the same markup in both modes; the defect is only in how it is drawn or answers.

/** A fixture page's body with its defect, and the same markup repaired. */
interface RenderVariant {
  readonly defect: string
  readonly valid: string
}

/**
 * A photo gallery on a pale ground. Its Previous and Next buttons carry an icon and no text, and a marker
 * button is drawn as a white disc with a ring. With `pale` set, the icons' strokes and the ring are too light
 * to stand out from the ground; repaired, the icons are dark and the pale ring gains a dark outer ring. Around
 * them, what must pass in both modes: a text button, a native checkbox and a link drawn as a dark icon.
 */
const GALLERY: string = [
  "'use client'",
  "import { useState } from 'react'",
  '',
  "const PHOTOS: readonly string[] = ['Harbour at dawn', 'Orchard in bloom', 'Library stairs', 'Winter field']",
  '',
  'export function Gallery({ pale }: { pale: boolean }): React.JSX.Element {',
  '  const [index, setIndex] = useState(0)',
  '  const [isMarked, setMarked] = useState(false)',
  '  const [isOpen, setOpen] = useState(false)',
  "  const stroke: string = pale ? '#cbd5e1' : '#1f2937'",
  '  const step = (by: number): void => setIndex((index + by + PHOTOS.length) % PHOTOS.length)',
  '  return (',
  '    <div className="gallery">',
  '      <p>{PHOTOS[index]}</p>',
  '      <div className="gallery-controls">',
  '        <button type="button" className="icon-button" aria-label="Previous photo" onClick={(): void => step(-1)}>',
  '          <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">',
  '            <path d="M15 5l-7 7 7 7" fill="none" stroke={stroke} strokeWidth="3" />',
  '          </svg>',
  '        </button>',
  '        <button type="button" className="icon-button" aria-label="Next photo" onClick={(): void => step(1)}>',
  '          <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">',
  '            <path d="M9 5l7 7-7 7" fill="none" stroke={stroke} strokeWidth="3" />',
  '          </svg>',
  '        </button>',
  '        <button',
  '          type="button"',
  "          className={pale ? 'marker marker-pale' : 'marker marker-ringed'}",
  '          aria-label="Mark this photo"',
  '          onClick={(): void => setMarked(!isMarked)}',
  '        />',
  '      </div>',
  "      <p>{isMarked ? 'This photo is marked.' : 'No photo is marked.'}</p>",
  '      <button type="button" className="album-button" onClick={(): void => setOpen(!isOpen)}>Open the album</button>',
  "      <p>{isOpen ? 'The album is open.' : 'The album is closed.'}</p>",
  '      <label className="captions"><input type="checkbox" name="captions" /> Show captions</label>',
  '      <a href="/" className="icon-link" aria-label="Back to the contract">',
  '        <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">',
  '          <path d="M4 12l8-8 8 8M7 10v10h10V10" fill="none" stroke="#1f2937" strokeWidth="3" />',
  '        </svg>',
  '      </a>',
  '    </div>',
  '  )',
  '}',
  '',
].join('\n')

/**
 * Colour swatches, each a button. With `touch` unset, one answers only a pointer that is a mouse and another
 * only where the device can hover, so both work for a mouse and do nothing for a finger; repaired, both answer
 * any pointer. Around them, what must pass in both modes: a swatch that answers every pointer, and one no
 * script answers at all, which a finger and a mouse find equally idle.
 */
const SWATCHES: string = [
  "'use client'",
  "import { useState } from 'react'",
  '',
  'export function Swatches({ touch }: { touch: boolean }): React.JSX.Element {',
  "  const [picked, setPicked] = useState('none')",
  '  const pickByPointer = (event: React.PointerEvent<HTMLButtonElement>): void => {',
  "    if (touch || event.pointerType === 'mouse') {",
  "      setPicked('moss')",
  '    }',
  '  }',
  '  const pickWhereHovering = (): void => {',
  "    if (touch || window.matchMedia('(hover: hover)').matches) {",
  "      setPicked('clay')",
  '    }',
  '  }',
  '  return (',
  '    <div className="swatches">',
  '      <p>Picked colour: {picked}</p>',
  '      <button type="button" className="swatch swatch-moss" onPointerUp={pickByPointer}>Moss</button>',
  '      <button type="button" className="swatch swatch-clay" onClick={pickWhereHovering}>Clay</button>',
  '      <button type="button" className="swatch swatch-slate" onClick={(): void => setPicked(\'slate\')}>Slate</button>',
  '      <button type="button" className="swatch swatch-sand">Sand</button>',
  '    </div>',
  '  )',
  '}',
  '',
].join('\n')

const NOTICE: string =
  'The reading room opens at nine and closes at six. Bring your card to borrow up to five books, and return ' +
  'them within three weeks so that other readers may enjoy them too.'

// Enough lanterns, each two fifths of a viewport tall in two columns, to fill the field's four viewports.
const LANTERNS: number = 18

// A lantern, its glass filling it.
const LANTERN: string = '<span className="lantern"><span className="lantern-glass" /></span>'

/** The client components these pages render, by the name a page imports each under. */
export const RENDER_COMPONENTS: Readonly<Record<string, string>> = { Gallery: GALLERY, Swatches: SWATCHES }

/** Each page's body in both modes, by its route. */
export const RENDER_PAGES: Readonly<Record<string, RenderVariant>> = {
  // A notice set over several lines. The defect breaks each line as late as it can, which `text-wrap: wrap`
  // spells by resetting the style the page inherits; repaired, the notice weighs its last lines.
  'text-wrap': {
    defect: `<p className="notice notice-ragged">${NOTICE}</p>`,
    valid: `<p className="notice">${NOTICE}</p>`,
  },
  'graphic-contrast': { defect: '<Gallery pale={true} />', valid: '<Gallery pale={false} />' },
  'tap-ignored': { defect: '<Swatches touch={false} />', valid: '<Swatches touch={true} />' },
  // An aurora banner and its companions. The defect blurs, frosts or masks a box with something moving
  // inside it, and animates a glow's own filter; repaired, the same boxes draw their effect as a still
  // gradient or a plain tint, and the glow keeps its blur still.
  'filter-in-motion': {
    defect:
      '<div className="aurora aurora-blurred"><span className="drifter" /></div>' +
      '<div className="pane pane-frosted"><span className="drifter" /></div>' +
      '<div className="pane pane-veiled"><span className="drifter" /></div>' +
      '<div className="glow glow-pulsing" />',
    valid:
      '<div className="aurora aurora-painted"><span className="drifter" /></div>' +
      '<div className="pane pane-tinted"><span className="drifter" /></div>' +
      '<div className="pane pane-tinted"><span className="drifter" /></div>' +
      '<div className="glow glow-still" />',
  },
  // A lantern field four viewports tall on a dark ground. The defect promotes the field, every lantern and
  // every lantern's glass to a layer of its own, three layers deep over what is shown; repaired, nothing in it
  // asks for a layer.
  'layer-area': {
    defect: `<div className="lantern-field lantern-promoted">${LANTERN.repeat(LANTERNS)}</div>`,
    valid: `<div className="lantern-field">${LANTERN.repeat(LANTERNS)}</div>`,
  },
}

/** The styles of these pages. */
export const RENDER_STYLES: string = `
.notice { max-width: 22rem; }
.notice-ragged { text-wrap: wrap; }
.gallery { display: flex; flex-direction: column; gap: 12px; padding: 16px; background: #f3f4f6; }
.gallery-controls { display: flex; gap: 16px; align-items: center; }
.icon-button { display: inline-flex; align-items: center; justify-content: center; width: 48px; height: 48px;
  min-width: 0; margin: 0; padding: 0; border: 0; background: transparent; }
.marker { width: 44px; height: 44px; min-width: 0; margin: 0 8px; padding: 0; border: 0; border-radius: 50%;
  background: #ffffff; }
.marker-pale { box-shadow: 0 0 0 3px #b7dfba; }
.marker-ringed { box-shadow: 0 0 0 3px #b7dfba, 0 0 0 5px #14532d; }
.album-button { margin: 0; color: #111827; background: #ffffff; border: 2px solid #111827; }
.captions { display: flex; align-items: center; gap: 8px; }
.icon-link { justify-content: center; }
.swatches { display: flex; flex-direction: column; gap: 12px; }
.swatch { margin: 0; color: #111827; border: 2px solid #111827; }
.swatch-moss { background: #ecfdf5; }
.swatch-clay { background: #fff7ed; }
.swatch-slate { background: #f1f5f9; }
.swatch-sand { background: #fefce8; }
.album-button:focus-visible, .swatch:focus-visible { outline: 3px solid #1d4ed8; outline-offset: 3px; }
.aurora { position: relative; height: 120px; overflow: hidden; }
.aurora-blurred { background: #0f766e; filter: blur(6px); }
.aurora-painted { background: radial-gradient(circle at 30% 40%, #5eead4, #0f766e 70%); }
.pane { position: relative; width: 200px; height: 100px; overflow: hidden; background: rgb(255 255 255 / 60%); }
.pane-frosted { backdrop-filter: blur(8px); }
.pane-veiled { mask-image: linear-gradient(to bottom, black 40%, transparent); }
.drifter { position: absolute; top: 20px; left: 20px; width: 24px; height: 24px; background: #134e4a; }
.glow { width: 120px; height: 120px; background: #f59e0b; filter: blur(4px); }
.lantern-field { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; height: 400svh; background: #1e293b; }
.lantern { display: block; height: 40svh; background: #fde68a; }
.lantern-glass { display: block; height: 100%; background: #fbbf24; }
.lantern-promoted, .lantern-promoted .lantern, .lantern-promoted .lantern-glass { will-change: transform; }
@keyframes drift { from { transform: translateX(0); } to { transform: translateX(120px); } }
@keyframes pulse { from { filter: blur(2px); } to { filter: blur(10px); } }
@media (prefers-reduced-motion: no-preference) {
  .drifter { animation: drift 3s linear infinite alternate; }
  .glow-pulsing { animation: pulse 2s linear infinite alternate; }
}
`
