import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { IssueHistory, IssueSummary } from '../issues/types'
import { IssueDetailPage } from './IssueDetailPage'

const api = vi.hoisted(() => ({ fetchIssue: vi.fn(), fetchIssueHistory: vi.fn(), verifyRepair: vi.fn() }))
vi.mock('../api/issues', () => api)
vi.mock('../auth/useAuth', () => ({
  useAuth: () => ({
    state: { status: 'signedIn', user: { id: 'u', email: 'a@b.co', displayName: 'Ana', emailVerified: true } },
  }),
}))

const claimed: IssueSummary = {
  id: 'i1',
  damageType: 'POTHOLE',
  status: 'REPAIR_CLAIMED',
  latitude: 4.67,
  longitude: -74.05,
  reportCount: 3,
  openedAt: '2026-10-20T12:00:00Z',
  agency: 'Unidad de Mantenimiento Vial',
  photoUrl: '/media/before.jpg',
  locality: 'Chapinero',
  officialChannel: null,
  petition: { radicado: 'SIM-2026-000007', filedAt: '2026-10-21T12:00:00Z', dueDate: '2026-11-12', businessDaysLeft: 0, overdue: false },
}

const history: IssueHistory = {
  steps: [
    { from: null, to: 'REPORTED', cause: 'CITIZEN_REPORT', at: '2026-10-20T12:00:00Z' },
    { from: 'REPORTED', to: 'FILED', cause: 'PETITION_FILED', at: '2026-10-21T12:00:00Z' },
    { from: 'FILED', to: 'AGENCY_RESPONDED', cause: 'AGENCY_RESPONSE', at: '2026-10-25T12:00:00Z' },
    { from: 'AGENCY_RESPONDED', to: 'REPAIR_CLAIMED', cause: 'AGENCY_RESPONSE', at: '2026-10-25T12:00:00Z' },
  ],
  agencyResponses: [
    { at: '2026-10-25T12:00:00Z', classification: 'RESOLVES', summary: 'La entidad dice que tapó el hueco.', body: 'Texto completo.' },
  ],
  verifications: [],
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('IssueDetailPage', () => {
  it('shows the timeline with the agency reply and asks for a verification photo', async () => {
    api.fetchIssue.mockResolvedValue(claimed)
    api.fetchIssueHistory.mockResolvedValue(history)
    render(<IssueDetailPage issueId="i1" />)

    expect(await screen.findByRole('heading', { name: 'Hueco en Chapinero' })).toBeTruthy()
    expect(screen.getByText('La entidad dice que tapó el hueco.')).toBeTruthy()
    expect(screen.getByText('Dice que lo reparó')).toBeTruthy()
    expect(screen.getByText('Unidad de Mantenimiento Vial dice que lo reparó')).toBeTruthy()
  })

  it('sends the photo and reloads the damage with its new status', async () => {
    api.fetchIssue.mockResolvedValueOnce(claimed).mockResolvedValue({ ...claimed, status: 'VERIFIED' })
    api.fetchIssueHistory.mockResolvedValueOnce(history).mockResolvedValue({
      ...history,
      steps: [...history.steps, { from: 'REPAIR_CLAIMED', to: 'VERIFIED', cause: 'PHOTO_VERIFICATION', at: '2026-10-26T12:00:00Z' }],
      verifications: [{ at: '2026-10-26T12:00:00Z', outcome: 'REPAIRED', reason: 'El hueco aparece tapado.', photoUrl: '/media/after.jpg' }],
    })
    api.verifyRepair.mockResolvedValue({ issueId: 'i1', outcome: 'REPAIRED', status: 'VERIFIED', reason: 'El hueco aparece tapado.', photoUrl: '/media/after.jpg' })
    render(<IssueDetailPage issueId="i1" />)

    await screen.findByText('Enviar foto de verificación')
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    fireEvent.change(input, { target: { files: [new File([new Uint8Array([1])], 'ahora.jpg', { type: 'image/jpeg' })] } })

    expect(await screen.findByText('Una foto confirmó la reparación')).toBeTruthy()
    expect(api.verifyRepair).toHaveBeenCalledWith('i1', expect.any(File))
    expect((screen.getByAltText('Después: foto de verificación más reciente') as HTMLImageElement).src).toContain(
      '/media/after.jpg',
    )
  })
})
