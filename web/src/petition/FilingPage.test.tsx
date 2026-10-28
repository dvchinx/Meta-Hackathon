import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { FilerProfile } from '../api/petitions'
import type { IssueSummary } from '../issues/types'
import { FilingPage } from './FilingPage'

const api = vi.hoisted(() => ({
  fetchIssue: vi.fn(),
  fetchFilerProfile: vi.fn(),
  filePetition: vi.fn(),
  saveFilerProfile: vi.fn(),
}))
vi.mock('../api/issues', () => ({ fetchIssue: api.fetchIssue }))
vi.mock('../api/petitions', () => ({
  fetchFilerProfile: api.fetchFilerProfile,
  filePetition: api.filePetition,
  saveFilerProfile: api.saveFilerProfile,
}))

const issue: IssueSummary = {
  id: 'i1',
  damageType: 'POTHOLE',
  status: 'REPORTED',
  latitude: 4.67,
  longitude: -74.05,
  reportCount: 2,
  openedAt: '2026-10-20T12:00:00Z',
  agency: 'Unidad de Mantenimiento Vial',
  photoUrl: null,
  locality: 'Chapinero',
  officialChannel: { url: 'https://bogota.gov.co/yo-participo/reporta-sdqs/huecos', phone: 'Línea 195' },
  petition: null,
}

const profile: FilerProfile = {
  idType: 'CC',
  idNumber: '1020304050',
  firstNames: 'Ana',
  lastNames: 'Ruiz',
  mobilePhone: '3001234567',
  address: null,
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('FilingPage', () => {
  it('files only after the citizen authorizes sharing their data with the agency', async () => {
    api.fetchIssue.mockResolvedValue(issue)
    api.fetchFilerProfile.mockResolvedValue(profile)
    api.filePetition.mockResolvedValue({
      petitionId: 'p1',
      issueId: 'i1',
      radicado: 'SIM-2026-000001',
      filedAt: '2026-10-27T15:00:00Z',
      dueDate: '2026-11-19',
      agency: 'Unidad de Mantenimiento Vial',
    })
    render(<FilingPage issueId="i1" />)

    const file = (await screen.findByRole('button', { name: 'Radicar petición' })) as HTMLButtonElement
    expect(screen.getByText('•••4050', { exact: false })).toBeTruthy()
    expect(file.disabled).toBe(true)

    fireEvent.click(screen.getByLabelText(/Autorizo compartir mis datos/))
    expect(file.disabled).toBe(false)
    fireEvent.click(file)

    await screen.findByText('Petición radicada')
    expect(api.filePetition).toHaveBeenCalledWith('i1')
    expect(screen.getByText('SIM-2026-000001')).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Ver e imprimir la petición' }).getAttribute('href')).toBe('#/peticion?id=p1')
    expect(screen.getByRole('link', { name: 'Usa el canal oficial' }).getAttribute('href')).toBe(issue.officialChannel!.url)
  })

  it('asks for the filing data first when the citizen has none', async () => {
    api.fetchIssue.mockResolvedValue(issue)
    api.fetchFilerProfile.mockResolvedValue(null)
    render(<FilingPage issueId="i1" />)

    const save = (await screen.findByRole('button', { name: 'Guardar y continuar' })) as HTMLButtonElement
    expect(save.disabled).toBe(true)
    expect((screen.getByRole('button', { name: 'Radicar petición' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('explains when the damage already has a petition in progress', async () => {
    api.fetchIssue.mockResolvedValue({
      ...issue,
      status: 'FILED',
      petition: {
        radicado: 'SIM-2026-000009',
        filedAt: '2026-10-27T15:00:00Z',
        dueDate: '2026-11-19',
        businessDaysLeft: 9,
        overdue: false,
      },
    })
    api.fetchFilerProfile.mockResolvedValue(profile)
    render(<FilingPage issueId="i1" />)

    expect(await screen.findByText('SIM-2026-000009')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Radicar petición' })).toBeNull()
  })
})
