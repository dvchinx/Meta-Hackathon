import { describe, expect, it } from 'vitest'
import { averageDaysOpen, isOverdue, localitiesOf, matches, NO_FILTERS, statsBy, within } from './filters'
import type { IssueSummary } from './types'

function issue(overrides: Partial<IssueSummary>): IssueSummary {
  return {
    id: 'x',
    damageType: 'POTHOLE',
    status: 'REPORTED',
    latitude: 4.65,
    longitude: -74.06,
    reportCount: 1,
    openedAt: '2026-10-20T12:00:00Z',
    agency: 'Unidad de Mantenimiento Vial',
    photoUrl: null,
    locality: 'Chapinero',
    officialChannel: null,
    petition: null,
    example: null,
    ...overrides,
  }
}

const overduePetition = {
  radicado: 'SIM-2026-000001',
  filedAt: '2026-09-01T12:00:00Z',
  dueDate: '2026-09-22',
  businessDaysLeft: 0,
  overdue: true,
}

describe('filters', () => {
  const pothole = issue({ id: 'a' })
  const tree = issue({ id: 'b', damageType: 'RISKY_TREE', locality: 'Suba', agency: 'Secretaría Distrital de Ambiente' })
  const late = issue({ id: 'c', status: 'FILED', petition: overduePetition })
  const fixed = issue({ id: 'd', status: 'VERIFIED' })
  const all = [pothole, tree, late, fixed]

  it('filters by status, type and locality together', () => {
    expect(all.filter((i) => matches(i, NO_FILTERS))).toHaveLength(4)
    expect(all.filter((i) => matches(i, { ...NO_FILTERS, status: 'open' })).map((i) => i.id)).toEqual(['a', 'b', 'c'])
    expect(all.filter((i) => matches(i, { ...NO_FILTERS, status: 'overdue' })).map((i) => i.id)).toEqual(['c'])
    expect(all.filter((i) => matches(i, { ...NO_FILTERS, status: 'verified' })).map((i) => i.id)).toEqual(['d'])
    expect(all.filter((i) => matches(i, { ...NO_FILTERS, types: ['RISKY_TREE'] })).map((i) => i.id)).toEqual(['b'])
    expect(all.filter((i) => matches(i, { status: 'open', types: [], locality: 'Suba' })).map((i) => i.id)).toEqual(['b'])
  })

  it('only a filed petition can be overdue', () => {
    expect(isOverdue(late)).toBe(true)
    expect(isOverdue({ ...late, status: 'AGENCY_RESPONDED' })).toBe(false)
  })

  it('groups by locality and agency, most open first', () => {
    const byLocality = statsBy(all, 'locality')
    expect(byLocality[0]).toEqual({ name: 'Chapinero', open: 2, overdue: 1, verified: 1 })
    expect(byLocality[1]).toEqual({ name: 'Suba', open: 1, overdue: 0, verified: 0 })
    expect(statsBy(all, 'agency').map((g) => g.name)).toEqual(['Unidad de Mantenimiento Vial', 'Secretaría Distrital de Ambiente'])
  })

  it('lists localities alphabetically and averages days open', () => {
    expect(localitiesOf(all)).toEqual(['Chapinero', 'Suba'])
    expect(averageDaysOpen(all, new Date('2026-10-30T12:00:00Z'))).toBe(10)
    expect(averageDaysOpen([fixed])).toBeNull()
  })

  it('knows what is inside the map view', () => {
    expect(within(pothole, [-74.1, 4.6, -74.0, 4.7])).toBe(true)
    expect(within(pothole, [-75, 6, -74.9, 6.1])).toBe(false)
  })
})
