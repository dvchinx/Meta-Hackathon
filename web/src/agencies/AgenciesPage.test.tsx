import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AgencyScorecard } from '../api/agencies'
import { AgenciesPage } from './AgenciesPage'

const api = vi.hoisted(() => ({ fetchScorecards: vi.fn() }))
vi.mock('../api/agencies', () => ({ fetchScorecards: api.fetchScorecards }))

const umv: AgencyScorecard = {
  agencyCode: 'UMV',
  agencyName: 'Unidad de Mantenimiento Vial',
  issues: 4,
  open: 3,
  verified: 1,
  averageDaysOpen: 38.6,
  filed: 4,
  overduePetitions: 1,
  resolvingResponses: 2,
  evasiveResponses: 1,
  repairClaims: 3,
  claimsConfirmed: 1,
  claimsRefuted: 1,
  examples: 2,
}

const eaab: AgencyScorecard = {
  ...umv,
  agencyCode: 'EAAB',
  agencyName: 'Empresa de Acueducto y Alcantarillado de Bogotá',
  issues: 1,
  open: 1,
  verified: 0,
  averageDaysOpen: 1,
  filed: 0,
  overduePetitions: 0,
  resolvingResponses: 0,
  evasiveResponses: 0,
  repairClaims: 0,
  claimsConfirmed: 0,
  claimsRefuted: 0,
  examples: 0,
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('AgenciesPage', () => {
  it('shows how each agency answers and whether its repairs hold up to a photo', async () => {
    api.fetchScorecards.mockResolvedValue([umv, eaab])
    render(<AgenciesPage />)

    const card = await screen.findByRole('article', { name: 'Unidad de Mantenimiento Vial' })
    expect(card.textContent).toContain('4 daños · 3 abiertos · 1 verificado')
    expect(card.textContent).toContain('39 días')
    expect(card.textContent).toContain('1 de 4 radicadas')
    expect(
      screen.getByRole('img', {
        name: 'De 3 reparaciones declaradas: 1 confirmadas con foto, 1 desmentidas con foto y 1 sin confirmar.',
      }),
    ).toBeTruthy()
    expect(screen.getByText(/Incluye 2 daños de ejemplo/)).toBeTruthy()

    const quiet = screen.getByRole('article', { name: /Empresa de Acueducto/ })
    expect(quiet.textContent).toContain('1 daño · 1 abierto')
    expect(quiet.querySelector('.claim-bar')).toBeNull()
  })

  it('says so when no damage has an agency yet', async () => {
    api.fetchScorecards.mockResolvedValue([])
    render(<AgenciesPage />)

    expect(await screen.findByText('Todavía no hay daños asignados a una entidad')).toBeTruthy()
  })
})
