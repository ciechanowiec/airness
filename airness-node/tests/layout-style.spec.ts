// What the layout measurement reads from one element's computed style, stated as the strings a browser computes.
import { describe, expect, it } from 'vitest'
import { bordersOf, flowOf, lineHeightOf, type PageStyle, paintOrderOf } from '@/layout-style'

const styleOf = (computed: Readonly<Record<string, string>>): PageStyle => ({
  getPropertyValue: (property: string): string => computed[property] ?? '',
})

describe('the computed style of one element', () => {
  it('gives each border its width in pixels, its style and its colour', () => {
    expect(
      bordersOf(
        styleOf({
          'border-top-width': '2px',
          'border-top-style': 'solid',
          'border-top-color': 'rgb(0, 0, 0)',
          'border-left-width': '0px',
          'border-left-style': 'none',
        }),
      ),
    ).toEqual({
      top: { width: 2, style: 'solid', color: 'rgb(0, 0, 0)' },
      right: { width: 0, style: '', color: '' },
      bottom: { width: 0, style: '', color: '' },
      left: { width: 0, style: 'none', color: '' },
    })
  })

  it('reads a line height in pixels, and normal as none', () => {
    expect([
      lineHeightOf(styleOf({ 'line-height': '24px' })),
      lineHeightOf(styleOf({ 'line-height': 'normal' })),
    ]).toEqual([24, 0])
  })

  it('reads the stacking order and any turn', () => {
    expect(paintOrderOf(styleOf({ 'z-index': '2', transform: 'none', rotate: '3deg' }))).toEqual({
      zIndex: '2',
      transform: 'none',
      rotate: '3deg',
    })
  })

  it('reads what can show its boxes, or its children, out of document order', () => {
    expect(
      flowOf(
        styleOf({
          order: '-1',
          'flex-direction': 'column-reverse',
          'flex-wrap': 'nowrap',
          'grid-auto-flow': 'row dense',
          'grid-row-start': '2',
          'grid-row-end': 'auto',
          'grid-column-start': 'span 2',
          'grid-column-end': 'auto',
          direction: 'rtl',
          'writing-mode': 'horizontal-tb',
        }),
      ),
    ).toEqual({
      order: '-1',
      flexDirection: 'column-reverse',
      flexWrap: 'nowrap',
      gridAutoFlow: 'row dense',
      gridLines: ['2', 'auto', 'span 2', 'auto'],
      direction: 'rtl',
      writingMode: 'horizontal-tb',
    })
  })
})
