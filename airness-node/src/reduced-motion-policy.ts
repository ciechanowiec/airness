// What a page owes a reader who asked the system for reduced motion (rule `reduced-motion`): content does not
// travel across the screen because the reader scrolled or opened something. WCAG 2.3.3 asks that motion set
// off by an interaction can be turned off, and `prefers-reduced-motion: reduce` is how a reader turns it off
// once for every site. Every other pass freezes animation under that preference to measure a still frame, so
// it cannot see motion the page leaves running; the scrolling pass loads one route per template again with the
// preference and without the freeze, scrolls and opens a disclosure, and records every animation that ran and
// every transform that changed. Everything that decides a verdict is here.
//
// WHAT COUNTS AS MOTION. An animation of a property that places content - a transform or one of its individual
// properties, an inset, a motion path, a margin - moves it; a fade or a colour change does not, and is left
// alone. A transition shorter than a frame or two is the usual reduced-motion reset, which keeps transition
// events firing while nothing visibly travels, so it is not motion either. Nor is an icon turning: only an
// element at least the size of a fingertip target is content a reader could lose their place in.
//
// WHAT COUNTS AS SET OFF BY THE READER. An animation already running before the interaction, such as a
// spinner or a marquee, is not one the interaction caused, and belongs to other criteria; neither is the
// motion of the control the reader pressed, such as a chevron turning or a button sinking as it is pressed.
// An animation driven by the scroll position rather than by time moves content as the reader scrolls, so it
// is judged whenever it runs, and so is an element a script moves on every scroll, which is parallax.

/** The animated properties that move content, by exact name. */
const MOTION_PROPERTIES: ReadonlySet<string> = new Set<string>([
  'transform',
  'translate',
  'rotate',
  'scale',
  'top',
  'left',
  'right',
  'bottom',
])

/** The animated properties that move content, by the prefix of their name. */
const MOTION_PREFIXES: readonly string[] = ['inset', 'offset-', 'margin']

/** The shortest animation that counts as motion, in milliseconds: shorter is a reset, not travel. */
const MOTION_MIN_MS: number = 100

/** The smallest element whose motion counts, in square CSS pixels: a 48px square, a fingertip target. */
const MOTION_MIN_AREA: number = 2304

/** One animated property and the values its keyframes give it, in order. */
export interface AnimatedProperty {
  /** The property in CSS spelling, such as `transform` or `margin-top`. */
  readonly name: string
  readonly values: readonly string[]
}

/** What the browser reports about one animation that ran while the reader acted. */
export interface AnimationSample {
  /** How the page made it. */
  readonly kind: 'css-transition' | 'css-animation' | 'web-animation'
  /** Whether it runs on the document's clock or on the scroll position. */
  readonly timeline: 'time' | 'scroll'
  readonly properties: readonly AnimatedProperty[]
  /** How long one run of all its iterations lasts, in milliseconds; null when it never ends. */
  readonly activeMs: number | null
  readonly width: number
  readonly height: number
  /** Whether it was already running before the interaction. */
  readonly wasRunningBefore: boolean
  /** Whether it animates the control the reader activated, or something inside it. */
  readonly isOnActivatedControl: boolean
}

/** What the browser reports about one element's transform as the reader scrolled. */
export interface TransformTrack {
  /** The element's transform at the start and the end of the still baseline, then after each scroll. */
  readonly values: readonly string[]
  readonly width: number
  readonly height: number
  /** Whether an animation drives it, which the animation samples judge instead. */
  readonly isAnimated: boolean
}

/** One keyframe as the browser reports it: a value per animated property, beside the keys that time it. */
type Keyframe = Readonly<Record<string, unknown>>

/** What the browser reads off one running animation, before it is described in the policy's terms. */
export interface RecordedAnimation {
  /** The animation's class: `CSSTransition`, `CSSAnimation`, or `Animation` for one a script made. */
  readonly constructorName: string
  /** Whether it runs on the document's own timeline rather than on a scroll or view timeline. */
  readonly isTimeDriven: boolean
  /** Its keyframes as the browser reports them: a value per animated property, beside the keys that time it. */
  readonly keyframes: readonly Readonly<Record<string, unknown>>[]
  /** Its computed active duration, in milliseconds: `Infinity` when it never ends. */
  readonly activeMs: number
  readonly width: number
  readonly height: number
  readonly wasRunningBefore: boolean
  readonly isOnActivatedControl: boolean
}

/** The keys of a keyframe that time it rather than name a property it animates. */
const TIMING_KEYS: ReadonlySet<string> = new Set<string>(['offset', 'computedOffset', 'easing', 'composite'])

// A keyframe names its properties in camel case, as the script API spells them; a custom property keeps its
// own spelling.
const cssName = (name: string): string =>
  name.startsWith('--') ? name : name.replaceAll(/[A-Z]/gu, (letter: string): string => `-${letter.toLowerCase()}`)

// The properties a set of keyframes animates, each with the values the frames give it in order, in CSS spelling.
const keyframeProperties = (frames: readonly Keyframe[]): readonly AnimatedProperty[] =>
  [...new Set<string>(frames.flatMap((frame: Keyframe): readonly string[] => Object.keys(frame)))]
    .filter((name: string): boolean => !TIMING_KEYS.has(name))
    .map(
      (name: string): AnimatedProperty => ({
        name: cssName(name),
        values: frames.flatMap((frame: Keyframe): readonly string[] => {
          const value: unknown = frame[name]
          return typeof value === 'string' || typeof value === 'number' ? [String(value)] : []
        }),
      }),
    )

const KINDS: Readonly<Record<string, AnimationSample['kind']>> = {
  CSSTransition: 'css-transition',
  CSSAnimation: 'css-animation',
}

// An endless animation reports an infinite duration; one whose duration could not be read lasts no time at all.
const activeMsOf = (activeMs: number): number | null => {
  if (activeMs === Number.POSITIVE_INFINITY) {
    return null
  }
  return Number.isFinite(activeMs) ? activeMs : 0
}

/**
 * An animation the browser recorded, in the terms the verdicts read.
 * @param recorded what the browser read off it.
 * @returns the sample.
 */
export const animationSampleOf = (recorded: RecordedAnimation): AnimationSample => ({
  kind: KINDS[recorded.constructorName] ?? 'web-animation',
  timeline: recorded.isTimeDriven ? 'time' : 'scroll',
  properties: keyframeProperties(recorded.keyframes),
  activeMs: activeMsOf(recorded.activeMs),
  width: recorded.width,
  height: recorded.height,
  wasRunningBefore: recorded.wasRunningBefore,
  isOnActivatedControl: recorded.isOnActivatedControl,
})

const isMotionProperty = (name: string): boolean =>
  MOTION_PROPERTIES.has(name) || MOTION_PREFIXES.some((prefix: string): boolean => name.startsWith(prefix))

// A property given one keyframe animates from the element's own value, which it differs from by being named;
// one given several moves only if they differ.
const isChanging = (property: AnimatedProperty): boolean =>
  property.values.length === 1 || new Set<string>(property.values).size > 1

// The animated properties of a sample that move content, each named once.
const movingProperties = (sample: AnimationSample): readonly string[] => [
  ...new Set<string>(
    sample.properties
      .filter((property: AnimatedProperty): boolean => isMotionProperty(property.name) && isChanging(property))
      .map((property: AnimatedProperty): string => property.name),
  ),
]

const isLargeEnough = (width: number, height: number): boolean => width * height >= MOTION_MIN_AREA

const isLongEnough = (sample: AnimationSample): boolean => sample.activeMs === null || sample.activeMs >= MOTION_MIN_MS

const isCausedByReader = (sample: AnimationSample): boolean =>
  sample.timeline === 'scroll' || !(sample.wasRunningBefore || sample.isOnActivatedControl)

/**
 * What is wrong with an animation that ran while the reader asked for reduced motion (rule `reduced-motion`).
 * @param label the animated element, as a finding names it.
 * @param sample the animation.
 * @param trigger what the reader did, such as `a scroll`.
 * @returns the message, or undefined when the animation moves no content the reader set moving.
 */
export const animationMessage = (label: string, sample: AnimationSample, trigger: string): string | undefined => {
  const moving: readonly string[] = movingProperties(sample)
  if (moving.length === 0 || !isLargeEnough(sample.width, sample.height) || !isCausedByReader(sample)) {
    return undefined
  }
  if (sample.timeline === 'scroll') {
    return `${label} moves with the scroll (${moving.join(', ')}) while the reader asks for reduced motion`
  }
  const lasting: string = sample.activeMs === null ? 'endlessly' : `${String(Math.round(sample.activeMs))}ms`
  return isLongEnough(sample)
    ? `${label} moves (${moving.join(', ')}, ${lasting}) after ${trigger} while the reader asks for reduced motion`
    : undefined
}

/**
 * What is wrong with an element a script moves as the reader scrolls (rule `reduced-motion`): its transform
 * held through the still baseline, then changed with each of the scrolls. One that changed once and
 * held, such as a header hidden on the way down, has not travelled with the scroll.
 * @param label the element, as a finding names it.
 * @param track its transform before and after each scroll.
 * @returns the message, or undefined when it did not move with the scroll.
 */
export const parallaxMessage = (label: string, track: TransformTrack): string | undefined => {
  const [start, rested, ...scrolled]: readonly string[] = track.values
  const isHeldStill: boolean = start !== undefined && start === rested
  const doesFollowEveryGesture: boolean =
    scrolled.length > 1 &&
    scrolled.every((value: string, index: number): boolean => value !== (index === 0 ? rested : scrolled[index - 1]))
  const isParallax: boolean = isHeldStill && doesFollowEveryGesture && isLargeEnough(track.width, track.height)
  return isParallax && !track.isAnimated
    ? `${label} moves with the scroll (its transform changes on every scroll, from ${String(rested)} to ` +
        `${String(scrolled.at(-1))}) while the reader asks for reduced motion`
    : undefined
}

/**
 * What is wrong with a document that scrolls smoothly to its own fragments while the reader asks for reduced
 * motion (rule `reduced-motion`): following an in-page link then slides the whole page instead of jumping.
 * @param scrollBehavior the root's computed `scroll-behavior`.
 * @param hasFragmentLinks whether the page links to fragments of itself.
 * @returns the message, or undefined when following such a link jumps.
 */
export const smoothScrollMessage = (scrollBehavior: string, hasFragmentLinks: boolean): string | undefined =>
  scrollBehavior === 'smooth' && hasFragmentLinks
    ? 'the document scrolls smoothly to its in-page links (scroll-behavior: smooth) while the reader asks for ' +
      'reduced motion'
    : undefined
