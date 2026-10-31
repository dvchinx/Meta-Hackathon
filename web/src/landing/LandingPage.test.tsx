import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { IssueSummary } from '../issues/types'
import { LandingPage } from './LandingPage'

const api = vi.hoisted(() => ({ fetchIssues: vi.fn(), fetchIssueHistory: vi.fn(), fetchScorecards: vi.fn() }))
vi.mock('../api/issues', () => ({ fetchIssues: api.fetchIssues, fetchIssueHistory: api.fetchIssueHistory }))
vi.mock('../api/agencies', () => ({ fetchScorecards: api.fetchScorecards }))

const base: IssueSummary = {
  id: 'i1',
  damageType: 'POTHOLE',
  status: 'FILED',
  latitude: 4.63,
  longitude: -74.15,
  reportCount: 3,
  openedAt: '2026-10-20T12:00:00Z',
  agency: 'Unidad de Mantenimiento Vial',
  photoUrl: '/media/cover-1.jpg',
  locality: 'Kennedy',
  officialChannel: null,
  petition: null,
  example: null,
}

const repaired: IssueSummary = {
  ...base,
  id: 'i2',
  status: 'VERIFIED',
  reportCount: 2,
  locality: 'Fontibón',
  photoUrl: '/media/cover-2.jpg',
  example: { credit: 'Foto base: autor (CC BY-SA 4.0), editada con IA.' },
}

beforeEach(() => {
  // No motion in tests: counters show their final value at once.
  vi.stubGlobal('matchMedia', (query: string) => ({ matches: query.includes('reduce'), media: query }))
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
  vi.unstubAllGlobals()
})

describe('LandingPage', () => {
  it('invites to report and shows live numbers and a real before/after', async () => {
    api.fetchIssues.mockResolvedValue([base, repaired])
    api.fetchScorecards.mockResolvedValue([])
    api.fetchIssueHistory.mockResolvedValue({
      steps: [],
      agencyResponses: [],
      verifications: [
        { at: '2026-10-28T12:00:00Z', outcome: 'REPAIRED', reason: 'El andén está seco.', photoUrl: '/media/after-2.jpg' },
      ],
    })
    render(<LandingPage />)

    expect(screen.getByRole('heading', { level: 1 }).textContent).toContain('no se quede en queja')
    expect(screen.getByRole('link', { name: 'Reportar un daño' }).getAttribute('href')).toBe('#/reportar')
    expect(screen.getByRole('link', { name: 'Ver el mapa' }).getAttribute('href')).toBe('#/mapa')

    const numbers = await screen.findByRole('region', { name: 'En cifras' })
    expect(numbers.textContent).toContain('2 daños en el mapa')
    expect(numbers.textContent).toContain('5 reportes y apoyos de vecinos')
    expect(numbers.textContent).toContain('1 reparaciones confirmadas con foto')
    expect(numbers.textContent).toContain('Incluye 1 daño de ejemplo')

    const after = await screen.findByAltText('Después: foto que confirmó la reparación')
    expect(after.getAttribute('src')).toBe('/media/after-2.jpg')
    expect(screen.getByAltText('Antes: foto del reporte original').getAttribute('src')).toBe('/media/cover-2.jpg')
    expect(api.fetchIssueHistory).toHaveBeenCalledWith('i2', expect.anything())
    expect(screen.getByText('Foto base: autor (CC BY-SA 4.0), editada con IA.')).toBeTruthy()
  })

  it('still reads well when the API is down', async () => {
    api.fetchIssues.mockRejectedValue(new Error('offline'))
    api.fetchScorecards.mockRejectedValue(new Error('offline'))
    render(<LandingPage />)

    expect(await screen.findByRole('heading', { name: /De la foto al arreglo/ })).toBeTruthy()
    expect(screen.queryByRole('region', { name: 'En cifras' })).toBeNull()
    expect(screen.queryByRole('slider')).toBeNull()
  })
})
