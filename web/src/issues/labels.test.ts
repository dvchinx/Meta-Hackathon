import { describe, expect, it } from 'vitest'
import { DAMAGE_LABELS, STATUS_COLORS, STATUS_LABELS, canFile, deadlineLabel, reportsLabel, timeOpen } from './labels'

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
    expect(Object.keys(DAMAGE_LABELS)).toHaveLength(11)
  })
})

describe('filing', () => {
  it('only when no petition is in progress and an agency is known', () => {
    expect(canFile({ status: 'REPORTED', agency: 'UMV' })).toBe(true)
    expect(canFile({ status: 'REOPENED', agency: 'UMV' })).toBe(true)
    expect(canFile({ status: 'FILED', agency: 'UMV' })).toBe(false)
    expect(canFile({ status: 'REPORTED', agency: null })).toBe(false)
  })

  it('describes the legal deadline', () => {
    const petition = { radicado: 'SIM-2026-000001', filedAt: '2026-10-27T15:00:00Z', dueDate: '2026-11-19' }
    expect(deadlineLabel({ ...petition, businessDaysLeft: 9, overdue: false })).toMatch(
      /^Quedan 9 días hábiles para responder \(vence el 19 (de )?nov/,
    )
    expect(deadlineLabel({ ...petition, businessDaysLeft: 1, overdue: false })).toMatch(/^Queda 1 día hábil/)
    expect(deadlineLabel({ ...petition, businessDaysLeft: 0, overdue: false })).toBe('El plazo para responder vence hoy')
    expect(deadlineLabel({ ...petition, businessDaysLeft: 0, overdue: true })).toMatch(/^Plazo vencido el 19 (de )?nov/)
  })
})
