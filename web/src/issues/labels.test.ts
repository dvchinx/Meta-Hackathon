import { describe, expect, it } from 'vitest'
import { DAMAGE_LABELS, STATUS_COLORS, STATUS_LABELS, reportsLabel, timeOpen } from './labels'

describe('timeOpen', () => {
  const now = new Date('2026-10-26T12:00:00Z')

  it('counts whole days', () => {
    expect(timeOpen('2026-10-26T08:00:00Z', now)).toBe('Abierto hoy')
    expect(timeOpen('2026-10-25T08:00:00Z', now)).toBe('Abierto hace 1 día')
    expect(timeOpen('2026-10-16T12:00:00Z', now)).toBe('Abierto hace 10 días')
  })

  it('never goes negative with clock skew', () => {
    expect(timeOpen('2026-10-27T12:00:00Z', now)).toBe('Abierto hoy')
  })
})

describe('labels', () => {
  it('pluralizes reports', () => {
    expect(reportsLabel(1)).toBe('1 reporte')
    expect(reportsLabel(4)).toBe('4 reportes')
  })

  it('only VERIFIED is shown as resolved', () => {
    const green = Object.entries(STATUS_COLORS).filter(([, color]) => color === STATUS_COLORS.VERIFIED)
    expect(green.map(([status]) => status)).toEqual(['VERIFIED'])
  })

  it('has a Spanish label for every status and damage type', () => {
    expect(Object.keys(STATUS_LABELS)).toHaveLength(7)
    expect(Object.keys(DAMAGE_LABELS)).toHaveLength(5)
  })
})
