// The sweep's rendering pass (rules `filter-in-motion` and `layer-area`): one route per template and one story
// per component, loaded at a phone's width and a desktop's with normal motion and nothing held still, are
// watched still and across two scroll steps. Every element that animates or whose placement changes is marked
// as moving; every element drawn through a filter or a mask is then read with whether it or anything inside it
// moved; and the browser's composited layers are read at rest and after each step through the Chrome DevTools
// Protocol. Every verdict is decided in `render-cost-policy`; the page functions are self-contained:
// Playwright ships them as text.
import type { CDPSession, Page } from '@playwright/test'
import { test } from '@playwright/test'
import { type BrowserFinding, type FindingPlace, findingAt, type Viewport } from './browser-findings.js'
import { STRESS_VIEWPORTS } from './browser-viewports.js'
import { type TargetFacts, targetFacts } from './layout.js'
import { projectSettings } from './project-settings.js'
import {
  type CompositedLayer,
  type FilterFacts,
  filterInMotionMessage,
  type LayerMeasure,
  layerAreaMessage,
  layerMeasure,
  RENDERING_RULES,
} from './render-cost-policy.js'
import { settleFindings } from './scanner.js'
import { carried } from './sweep-interaction.js'
import {
  armBudget,
  type Budget,
  type ComputedStyleOf,
  type Discovery,
  type DomDocument,
  EMPTY_VISIT,
  finishPass,
  healthOf,
  isChromium,
  loadLocation,
  merged,
  placeAt,
  preparePage,
  readDiscovery,
  type Shard,
  type ShardVisits,
  type SweepLocation,
  sampledLocations,
  shardLocations,
  type Visit,
  visitAll,
} from './sweep-shared.js'

// The browser surface the page functions below touch, declared for the reason `settle.ts` records.
declare const document: DomDocument
declare const getComputedStyle: ComputedStyleOf

/** The attribute marking each element seen animating, or whose placement changed, while the page was watched. */
const MOVING_ATTRIBUTE: string = 'data-airness-moving'

/** The attribute naming, for one reading, each element drawn through a filter or a mask, by its index. */
const FILTERED_ATTRIBUTE: string = 'data-airness-filtered'

/** The attribute each element keeps its last placement in, between readings. */
const PLACEMENT_ATTRIBUTE: string = 'data-airness-placement'

/** How long the page is watched at rest and after each scroll, in milliseconds. */
const WATCH_MS: number = 600

/** How often the page reads its running animations while it is watched: about once a frame. */
const POLL_MS: number = 16

/** How far each of the two scrolls moves the document, as a share of the viewport's height. */
const SCROLL_SHARE: number = 0.5

/** How long the browser has to report its layers after it is asked, in milliseconds. */
const LAYERS_MS: number = 3000

/** One place the pass loads a location at. */
interface RenderingPlace {
  readonly location: SweepLocation
  readonly viewport: Viewport
}

// Watches the running animations for the given time, about once a frame, and marks each one's element as
// moving with the properties it changes. Recursive rather than a loop, as the settle is: each step awaits a
// timer, so what grows is time rather than stack.
const watchAnimationsInPage = async ([attribute, ms, pollMs]: readonly [string, number, number]): Promise<void> => {
  const ignored: ReadonlySet<string> = new Set<string>(['offset', 'easing', 'composite', 'computedOffset'])
  const mark = (): void => {
    const running: readonly KeyframeEffect[] = document
      .getAnimations()
      .filter((animation: Animation): boolean => animation.playState === 'running')
      .map((animation: Animation): AnimationEffect | null => animation.effect)
      .filter((effect: AnimationEffect | null): effect is KeyframeEffect => effect instanceof KeyframeEffect)
    for (const effect of running) {
      const target: Element | null = effect.target
      const properties: readonly string[] = effect
        .getKeyframes()
        .flatMap((frame: ComputedKeyframe): readonly string[] => Object.keys(frame))
        .filter((key: string): boolean => !ignored.has(key))
        .map((key: string): string => key.replaceAll(/[A-Z]/gu, (letter: string): string => `-${letter.toLowerCase()}`))
      const earlier: readonly string[] = (target?.getAttribute(attribute) ?? '').split(' ').filter(Boolean)
      target?.setAttribute(attribute, [...new Set<string>([...earlier, ...properties, 'animated'])].join(' '))
    }
  }
  const watch = async (remaining: number): Promise<void> => {
    mark()
    if (remaining <= 0) {
      return
    }
    await new Promise<void>((resolve: () => void): void => {
      setTimeout(resolve, pollMs)
    })
    await watch(remaining - pollMs)
  }
  await watch(ms)
}

// Reads every rendered element's placement - its transforms and its insets - and marks as moving each whose
// placement differs from the reading before, which the element keeps in an attribute of its own. The first
// reading of an element only records it.
const readPlacementsInPage = ([moving, placed]: readonly [string, string]): void => {
  const elements: readonly Element[] = Array.from(document.querySelectorAll('body *')).filter(
    (element: Element): boolean => element.checkVisibility(),
  )
  for (const element of elements) {
    const style: CSSStyleDeclaration = getComputedStyle(element)
    const placement: string = [style.transform, style.translate, style.rotate, style.scale, style.inset].join(' ')
    const previous: string | null = element.getAttribute(placed)
    if (previous !== null && previous !== placement && !element.hasAttribute(moving)) {
      element.setAttribute(moving, 'placed')
    }
    element.setAttribute(placed, placement)
  }
}

const scrollInPage = (share: number): void => {
  scrollBy({ top: document.documentElement.clientHeight * share, behavior: 'instant' })
}

// Every rendered element drawn through a filter, a backdrop filter or a mask, marked with its index, with
// whether it or anything inside it was marked as moving.
const filterFactsInPage = ([filtered, moving]: readonly [string, string]): readonly FilterFacts[] => {
  const earlier: readonly Element[] = Array.from(document.querySelectorAll(`[${CSS.escape(filtered)}]`))
  for (const marked of earlier) {
    marked.removeAttribute(filtered)
  }
  const shaded: readonly (readonly [Element, CSSStyleDeclaration])[] = Array.from(
    document.querySelectorAll('body *'),
    (element: Element): readonly [Element, CSSStyleDeclaration] => [element, getComputedStyle(element)],
  ).filter(
    ([element, style]: readonly [Element, CSSStyleDeclaration]): boolean =>
      element.checkVisibility() &&
      (style.filter !== 'none' ||
        style.backdropFilter !== 'none' ||
        style.maskImage !== 'none' ||
        (style.getPropertyValue('-webkit-mask-image') || 'none') !== 'none'),
  )
  const selector: string = `[${CSS.escape(moving)}]`
  return shaded.map(([element, style]: readonly [Element, CSSStyleDeclaration], index: number): FilterFacts => {
    element.setAttribute(filtered, String(index))
    const box: DOMRect = element.getBoundingClientRect()
    const marks: readonly string[] = (element.getAttribute(moving) ?? '').split(' ').filter(Boolean)
    return {
      width: box.width,
      height: box.height,
      hasBlur: /blur\(|drop-shadow\(/u.test(style.filter),
      hasBackdrop: style.backdropFilter !== 'none',
      hasMask: style.maskImage !== 'none' || (style.getPropertyValue('-webkit-mask-image') || 'none') !== 'none',
      isMoving: marks.length > 0,
      hasMovingInside: element.querySelector(selector) !== null,
      animatedProperties: marks.filter((mark: string): boolean => mark !== 'animated' && mark !== 'placed'),
    }
  })
}

/** One layer as the LayerTree domain reports it. */
interface ProtocolLayer {
  readonly layerId: string
  readonly backendNodeId?: number
  readonly width: number
  readonly height: number
  readonly drawsContent: boolean
}

// Waits for the next report of the layer tree that carries its layers; none within the wait reads as none.
const nextLayers = async (session: CDPSession): Promise<readonly ProtocolLayer[]> =>
  await Promise.race([
    new Promise<readonly ProtocolLayer[]>((resolve: (layers: readonly ProtocolLayer[]) => void): void => {
      const listener = (event: { readonly layers?: readonly ProtocolLayer[] }): void => {
        if (event.layers === undefined) {
          return
        }
        session.off('LayerTree.layerTreeDidChange', listener)
        resolve(event.layers)
      }
      session.on('LayerTree.layerTreeDidChange', listener)
    }),
    new Promise<readonly ProtocolLayer[]>((resolve: (layers: readonly ProtocolLayer[]) => void): void => {
      setTimeout((): void => {
        resolve([])
      }, LAYERS_MS)
    }),
  ])

const QUAD_POINTS: number = 4
// A quad lists each point's x, then its y.
const QUAD_AXES: number = 2
// A layer's element is named by its tag and its id, or its first two classes, as other findings name one.
const LABEL_CLASSES: number = 2

// The centre of the element a layer draws, and how a finding names it; nothing for a layer of no element.
const elementOf = async (
  session: CDPSession,
  backendNodeId: number | undefined,
): Promise<Pick<CompositedLayer, 'centreX' | 'centreY' | 'label'>> => {
  if (backendNodeId === undefined) {
    return { centreX: undefined, centreY: undefined, label: 'a layer of no element' }
  }
  try {
    const { model } = await session.send('DOM.getBoxModel', { backendNodeId })
    const { node } = await session.send('DOM.describeNode', { backendNodeId })
    const xs: readonly number[] = model.border.filter(
      (_value: number, index: number): boolean => index % QUAD_AXES === 0,
    )
    const ys: readonly number[] = model.border.filter(
      (_value: number, index: number): boolean => index % QUAD_AXES === 1,
    )
    const attributes: readonly string[] = node.attributes ?? []
    const attributeOf = (name: string): string => attributes[attributes.indexOf(name) + 1] ?? ''
    const id: string = attributes.includes('id') ? `#${attributeOf('id')}` : ''
    const classes: string = attributes.includes('class')
      ? attributeOf('class')
          .trim()
          .split(/\s+/u)
          .slice(0, LABEL_CLASSES)
          .map((name: string): string => `.${name}`)
          .join('')
      : ''
    return {
      centreX: xs.reduce((sum: number, value: number): number => sum + value, 0) / QUAD_POINTS,
      centreY: ys.reduce((sum: number, value: number): number => sum + value, 0) / QUAD_POINTS,
      label: `${node.nodeName.toLowerCase()}${id || classes}`,
    }
  } catch {
    // A node without a box - a document, a node the page removed - draws nothing a viewport shows.
    return { centreX: undefined, centreY: undefined, label: 'a layer of no element' }
  }
}

// Why the browser gave a layer its own; a layer gone since the tree was reported has no reasons.
const reasonsOf = async (session: CDPSession, layerId: string): Promise<readonly string[]> => {
  try {
    const { compositingReasonIds } = await session.send('LayerTree.compositingReasons', { layerId })
    return compositingReasonIds
  } catch {
    return []
  }
}

// The layers the browser composites now: the tree is asked for afresh, since enabling the domain reports it.
const readLayers = async (session: CDPSession, documentNode: number): Promise<readonly CompositedLayer[]> => {
  await session.send('LayerTree.disable')
  const reported: Promise<readonly ProtocolLayer[]> = nextLayers(session)
  await session.send('LayerTree.enable')
  const layers: readonly ProtocolLayer[] = await reported
  return await Promise.all(
    layers.map(async (layer: ProtocolLayer): Promise<CompositedLayer> => {
      return {
        width: layer.width,
        height: layer.height,
        drawsContent: layer.drawsContent,
        reasons: await reasonsOf(session, layer.layerId),
        isDocument: layer.backendNodeId === documentNode,
        ...(await elementOf(session, layer.backendNodeId)),
      }
    }),
  )
}

const filterVisits = async (page: Page, place: FindingPlace, viewport: Viewport): Promise<Visit> => {
  const facts: readonly FilterFacts[] = await page.evaluate(filterFactsInPage, [
    FILTERED_ATTRIBUTE,
    MOVING_ATTRIBUTE,
  ] as const)
  const visits: readonly Visit[] = await Promise.all(
    facts.map(async (fact: FilterFacts, index: number): Promise<Visit> => {
      if (filterInMotionMessage('', fact, viewport) === undefined) {
        return EMPTY_VISIT
      }
      const target: TargetFacts | undefined = await targetFacts(page, `[${FILTERED_ATTRIBUTE}="${String(index)}"]`)
      const message: string | undefined = filterInMotionMessage(target?.label ?? 'an element', fact, viewport)
      return message === undefined ? EMPTY_VISIT : carried(findingAt(place, 'filter-in-motion', message), target)
    }),
  )
  return merged(visits)
}

// Watches the page still, then across two scroll steps, reading the layers at rest and after each step.
const sampleRendering = async (page: Page, session: CDPSession, viewport: Viewport): Promise<LayerMeasure> => {
  const { root } = await session.send('DOM.getDocument', { depth: 0 })
  const watch = async (): Promise<readonly CompositedLayer[]> => {
    await page.evaluate(watchAnimationsInPage, [MOVING_ATTRIBUTE, WATCH_MS, POLL_MS] as const)
    await page.evaluate(readPlacementsInPage, [MOVING_ATTRIBUTE, PLACEMENT_ATTRIBUTE] as const)
    return await readLayers(session, root.backendNodeId)
  }
  await page.evaluate(readPlacementsInPage, [MOVING_ATTRIBUTE, PLACEMENT_ATTRIBUTE] as const)
  const rested: readonly CompositedLayer[] = await watch()
  await page.evaluate(scrollInPage, SCROLL_SHARE)
  const once: readonly CompositedLayer[] = await watch()
  await page.evaluate(scrollInPage, SCROLL_SHARE)
  const twice: readonly CompositedLayer[] = await watch()
  return [rested, once, twice]
    .map((layers: readonly CompositedLayer[]): LayerMeasure => layerMeasure(layers, viewport))
    .reduce(
      (largest: LayerMeasure, measure: LayerMeasure): LayerMeasure =>
        measure.viewports > largest.viewports ? measure : largest,
      { viewports: 0, largest: [] },
    )
}

const renderingVisit = async (page: Page, item: RenderingPlace): Promise<Visit> => {
  const place: FindingPlace = placeAt(page, item.location, item.viewport, 'rendering')
  const loaded: readonly BrowserFinding[] = await loadLocation(page, item.location, item.viewport, {
    ...place,
    motion: 'no-preference',
  })
  const unsettled: readonly BrowserFinding[] = await settleFindings(page, place, false)
  const session: CDPSession = await page.context().newCDPSession(page)
  try {
    const measure: LayerMeasure = await sampleRendering(page, session, item.viewport)
    const layers: string | undefined = layerAreaMessage(measure, projectSettings.layerAreaBudget)
    return merged([
      {
        ...EMPTY_VISIT,
        findings: [...loaded, ...unsettled, ...(layers === undefined ? [] : [findingAt(place, 'layer-area', layers)])],
      },
      await filterVisits(page, place, item.viewport),
      await healthOf(page, place),
    ])
  } finally {
    await session.detach()
  }
}

/**
 * Load one shard of one route per template and one story per component at a phone's width and a desktop's,
 * with normal motion and nothing held still, and judge whether a filter or a mask is computed again on every
 * frame and whether the page's composited layers stay within the budget.
 * @param page the test's page.
 * @param shard the part of the pass this test visits.
 */
export const expectRendering = async (page: Page, shard: Shard): Promise<void> => {
  test.info().skip(!isChromium(page), 'the rendering pass reads Chromium layers and runs in Chromium')
  const discovery: Discovery = readDiscovery(test.info())
  const places: readonly RenderingPlace[] = sampledLocations(discovery).flatMap(
    (location: SweepLocation): readonly RenderingPlace[] =>
      STRESS_VIEWPORTS.map((viewport: Viewport): RenderingPlace => ({ location, viewport })),
  )
  const budget: Budget = armBudget(page, 'rendering', shard, places.length)
  await preparePage(page)
  const { visits, taken }: ShardVisits<RenderingPlace> = await visitAll(places, {
    budget,
    labelOf: (item: RenderingPlace): string => `${item.location.location} @${String(item.viewport.width)}`,
    visit: async (item: RenderingPlace): Promise<Visit> => await renderingVisit(page, item),
  })
  finishPass(page, {
    budget,
    pass: 'rendering',
    visits,
    judgedRules: RENDERING_RULES,
    locations: shardLocations(taken, (item: RenderingPlace): SweepLocation => item.location),
  })
}
