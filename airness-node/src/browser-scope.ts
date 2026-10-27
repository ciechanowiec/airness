// A trusted stack layer knows the roots of framework-owned views. The node layer owns the scan and
// the temporary marker; the upper layer supplies only selectors from its installed package.
import { pathToFileURL } from 'node:url'
import type { Page } from '@playwright/test'
import { isArray, readKey } from './json-shapes.js'
import { type LayerPackage, layerBrowserConfigOf, layerPackagesFor } from './layer.js'
import { readJson } from './project.js'

const VIEW_ATTRIBUTE: string = 'data-airness-view'
/** Roots marked by a trusted layer for the duration of an in-place scan. */
export const VIEW_ROOT_SELECTOR: string = `[${VIEW_ATTRIBUTE}]`

const selectorsOf = async (layer: LayerPackage): Promise<readonly string[]> => {
  const file: string | undefined = layerBrowserConfigOf(layer, process.cwd())
  if (file === undefined) {
    return []
  }
  const adapter: unknown = await import(pathToFileURL(file).href)
  const selectors: unknown = readKey(adapter, 'scanRootSelectors')
  if (
    !(
      isArray(selectors) &&
      selectors.every((value: unknown): value is string => typeof value === 'string' && value.trim().length > 0)
    )
  ) {
    throw new TypeError(`the browser adapter of ${layer.name} must export scanRootSelectors as nonempty selectors`)
  }
  return selectors
}

/** Mark only roots a trusted layer declares; an ordinary page remains wholly in scope. */
export const hasMarkedLayerView = async (page: Page): Promise<boolean> => {
  const directory: string = process.cwd()
  const layers: readonly LayerPackage[] = layerPackagesFor(directory, readJson(`${directory}/package.json`), readJson)
  const groups: readonly (readonly string[])[] = await Promise.all(
    layers.map(async (layer: LayerPackage): Promise<readonly string[]> => await selectorsOf(layer)),
  )
  const selectors: readonly string[] = groups.flat()
  if (selectors.length === 0) {
    return false
  }
  return await page.evaluate(hasMarkedInPage, [selectors.join(', '), VIEW_ATTRIBUTE] as const)
}

/** Remove the temporary markers even when the scan fails. */
export const unmarkLayerView = async (page: Page): Promise<void> => {
  await page.evaluate(unmarkInPage, [VIEW_ROOT_SELECTOR, VIEW_ATTRIBUTE] as const)
}

const hasMarkedInPage = ([selector, attribute]: readonly [string, string]): boolean => {
  const roots: readonly Element[] = Array.from(document.querySelectorAll(selector))
  for (const root of roots) {
    root.setAttribute(attribute, '')
  }
  return roots.length > 0
}

const unmarkInPage = ([selector, attribute]: readonly [string, string]): void => {
  for (const root of document.querySelectorAll(selector)) {
    root.removeAttribute(attribute)
  }
}
