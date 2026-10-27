import { afterEach, describe, expect, it, vi } from 'vitest'
import { ReportApiError, confirmReport, submitReport } from './reports'

afterEach(() => vi.unstubAllGlobals())

const photo = new File([new Uint8Array([0xff, 0xd8])], 'hueco.jpg', { type: 'image/jpeg' })

describe('submitReport', () => {
  it('sends photo, location, text and the reporter token as multipart', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ reportId: 'r1' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await submitReport({ photo, latitude: 4.65, longitude: -74.06, text: '  muy profundo  ' })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/reports')
    const body = init.body as FormData
    expect(body.get('photo')).toBeInstanceOf(File)
    expect(body.get('latitude')).toBe('4.65')
    expect(body.get('text')).toBe('muy profundo')
    expect(init.headers['X-Reporter-Token']).toMatch(/^[0-9a-f]{48}$/)
  })

  it('omits empty text', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await submitReport({ photo, latitude: 4.65, longitude: -74.06, text: '   ' })

    expect((fetchMock.mock.calls[0][1].body as FormData).has('text')).toBe(false)
  })

  it('surfaces the Spanish message from API errors', async () => {
    const error = { code: 'OUTSIDE_BOGOTA', message: 'Por ahora solo recibimos reportes en Bogotá.' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(error), { status: 422 })))

    await expect(submitReport({ photo, latitude: 6.24, longitude: -75.58 })).rejects.toMatchObject({
      code: 'OUTSIDE_BOGOTA',
      message: 'Por ahora solo recibimos reportes en Bogotá.',
    })
  })

  it('falls back to a generic message for non-JSON errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>', { status: 502 })))

    const error = await submitReport({ photo, latitude: 4.6, longitude: -74.08 }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ReportApiError)
    expect((error as ReportApiError).code).toBe('HTTP_502')
  })
})

describe('confirmReport', () => {
  it('posts the answer with the same reporter token', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await submitReport({ photo, latitude: 4.65, longitude: -74.06 })
    await confirmReport('abc', true)

    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe('/api/reports/abc/confirmation')
    expect(JSON.parse(init.body)).toEqual({ accepted: true })
    expect(init.headers['X-Reporter-Token']).toBe(fetchMock.mock.calls[0][1].headers['X-Reporter-Token'])
  })
})
