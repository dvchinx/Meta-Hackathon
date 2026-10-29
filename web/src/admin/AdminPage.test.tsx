import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { IssueSummary } from '../issues/types'
import { AdminPage } from './AdminPage'

const api = vi.hoisted(() => ({ fetchOverview: vi.fn(), registerAgencyResponse: vi.fn(), fetchIssues: vi.fn() }))
vi.mock('../api/admin', () => ({ fetchOverview: api.fetchOverview, registerAgencyResponse: api.registerAgencyResponse }))
vi.mock('../api/issues', () => ({ fetchIssues: api.fetchIssues }))

const filed: IssueSummary = {
  id: 'i1',
  damageType: 'POTHOLE',
  status: 'FILED',
  latitude: 4.67,
  longitude: -74.05,
  reportCount: 2,
  openedAt: '2026-10-20T12:00:00Z',
  agency: 'Unidad de Mantenimiento Vial',
  photoUrl: null,
  locality: 'Chapinero',
  officialChannel: null,
  petition: { radicado: 'SIM-2026-000003', filedAt: '2026-10-21T12:00:00Z', dueDate: '2026-11-12', businessDaysLeft: 6, overdue: false },
  example: null,
}

beforeEach(() => sessionStorage.clear())
afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('AdminPage', () => {
  it('asks for the token, then registers an agency reply that Muse Spark classifies', async () => {
    api.fetchOverview.mockResolvedValue({
      metaEnabled: false,
      spentUsd: 1.25,
      budgetUsd: 120,
      issuesByStatus: { FILED: 1 },
      manualReview: [],
    })
    api.fetchIssues.mockResolvedValue([filed])
    api.registerAgencyResponse.mockResolvedValue({
      issueId: 'i1',
      classification: 'RESOLVES',
      summary: 'Dice que tapó el hueco.',
      status: 'REPAIR_CLAIMED',
    })
    render(<AdminPage />)

    fireEvent.change(screen.getByLabelText('Código de administración'), { target: { value: 'secreto' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))

    expect(await screen.findByText('Peticiones esperando respuesta (1)')).toBeTruthy()
    expect(api.fetchOverview).toHaveBeenCalledWith('secreto')
    fireEvent.click(screen.getByRole('button', { name: 'Dice que lo reparó' }))
    fireEvent.click(screen.getByRole('button', { name: 'Registrar respuesta' }))

    expect(await screen.findByText(/Dice que tapó el hueco/)).toBeTruthy()
    expect(api.registerAgencyResponse).toHaveBeenCalledWith('secreto', 'i1', expect.stringContaining('quedó reparado'))
  })
})
