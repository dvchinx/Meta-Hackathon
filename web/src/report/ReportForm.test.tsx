import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReportAnalysis } from '../api/reports'
import { ReportForm } from './ReportForm'

// MapLibre needs WebGL, which jsdom lacks: a button stands in for tapping the map.
vi.mock('./LocationPicker', () => ({
  LocationPicker: ({ onChange }: { onChange: (l: { latitude: number; longitude: number }) => void }) => (
    <button type="button" onClick={() => onChange({ latitude: 4.6486, longitude: -74.0628 })}>
      marcar punto
    </button>
  ),
}))

const api = vi.hoisted(() => ({ submitReport: vi.fn(), confirmReport: vi.fn() }))
vi.mock('../api/reports', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/reports')>()),
  ...api,
}))

const analysis: ReportAnalysis = {
  reportId: 'r1',
  outcome: 'AWAITING_CONFIRMATION',
  damageType: 'POTHOLE',
  urgency: 'HIGH',
  summary: 'Hueco profundo en el carril derecho.',
  locationHints: ['Calle 60 con Carrera 7'],
  agency: 'Instituto de Desarrollo Urbano',
  markedPhotoUrl: '/media/m1.jpg',
}

beforeEach(() => {
  URL.createObjectURL = vi.fn(() => 'blob:preview')
  URL.revokeObjectURL = vi.fn()
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function fillForm() {
  const input = document.querySelector('input[type="file"]') as HTMLInputElement
  fireEvent.change(input, { target: { files: [new File([new Uint8Array([1])], 'f.jpg', { type: 'image/jpeg' })] } })
  fireEvent.click(screen.getByText('marcar punto'))
}

describe('ReportForm', () => {
  it('needs a photo and a location before sending', () => {
    render(<ReportForm onShowMap={() => {}} />)
    const send = screen.getByRole('button', { name: 'Enviar reporte' }) as HTMLButtonElement
    expect(send.disabled).toBe(true)

    fillForm()

    expect(send.disabled).toBe(false)
  })

  it('shows the marked photo and creates the Issue on confirmation', async () => {
    api.submitReport.mockResolvedValue(analysis)
    api.confirmReport.mockResolvedValue({ reportId: 'r1', accepted: true, issueId: 'i1', newIssue: false, reportCount: 3 })
    const onShowMap = vi.fn()
    render(<ReportForm onShowMap={onShowMap} />)

    fillForm()
    fireEvent.click(screen.getByRole('button', { name: 'Enviar reporte' }))

    await screen.findByText('Revisa tu reporte')
    expect(screen.getByText('Hueco')).toBeTruthy()
    expect(screen.getByText('Alta')).toBeTruthy()
    expect(screen.getByText('Instituto de Desarrollo Urbano')).toBeTruthy()
    expect(screen.getByText('Calle 60 con Carrera 7')).toBeTruthy()
    expect((screen.getByAltText('Foto con el daño marcado') as HTMLImageElement).src).toContain('/media/m1.jpg')
    expect(api.submitReport.mock.calls[0][0]).toMatchObject({ latitude: 4.6486, longitude: -74.0628 })

    fireEvent.click(screen.getByRole('button', { name: 'Sí, es correcto' }))

    await screen.findByText(/ahora son 3 reportes/)
    fireEvent.click(screen.getByRole('button', { name: 'Ver en el mapa' }))
    expect(onShowMap).toHaveBeenCalled()
    expect(api.confirmReport).toHaveBeenCalledWith('r1', true)
  })

  it('asks for another photo when it is rejected and keeps the location', async () => {
    api.submitReport.mockResolvedValue({ ...analysis, outcome: 'PHOTO_REJECTED', markedPhotoUrl: null })
    render(<ReportForm onShowMap={() => {}} />)

    fillForm()
    fireEvent.click(screen.getByRole('button', { name: 'Enviar reporte' }))

    await screen.findByText(/No identificamos un daño/)
    const send = screen.getByRole('button', { name: 'Enviar reporte' }) as HTMLButtonElement
    expect(send.disabled).toBe(true) // photo cleared…
    const input = document.querySelector('input[type="file"]') as HTMLInputElement
    fireEvent.change(input, { target: { files: [new File([new Uint8Array([2])], 'g.jpg', { type: 'image/jpeg' })] } })
    expect(send.disabled).toBe(false) // …location kept
  })

  it('shows API errors in Spanish and lets the citizen retry', async () => {
    const { ReportApiError } = await import('../api/reports')
    api.submitReport.mockRejectedValue(new ReportApiError(422, 'OUTSIDE_BOGOTA', 'Por ahora solo recibimos reportes en Bogotá.'))
    render(<ReportForm onShowMap={() => {}} />)

    fillForm()
    fireEvent.click(screen.getByRole('button', { name: 'Enviar reporte' }))

    await screen.findByText('Por ahora solo recibimos reportes en Bogotá.')
    expect(screen.getByRole('button', { name: 'Enviar reporte' })).toBeTruthy()
  })

  it('rejects files that are not photos', () => {
    render(<ReportForm onShowMap={() => {}} />)
    const input = document.querySelector('input[type="file"]') as HTMLInputElement

    fireEvent.change(input, { target: { files: [new File(['x'], 'doc.pdf', { type: 'application/pdf' })] } })

    expect(screen.getByText('Elige una foto (JPG, PNG o WebP).')).toBeTruthy()
  })

  it('saying no discards the report', async () => {
    api.submitReport.mockResolvedValue(analysis)
    api.confirmReport.mockResolvedValue({ reportId: 'r1', accepted: false, issueId: null, newIssue: false, reportCount: 0 })
    render(<ReportForm onShowMap={() => {}} />)

    fillForm()
    fireEvent.click(screen.getByRole('button', { name: 'Enviar reporte' }))
    await screen.findByText('Revisa tu reporte')
    fireEvent.click(screen.getByRole('button', { name: 'No, descartar' }))

    await waitFor(() => expect(screen.getByText(/Descartamos ese reporte/)).toBeTruthy())
    expect(api.confirmReport).toHaveBeenCalledWith('r1', false)
  })
})
