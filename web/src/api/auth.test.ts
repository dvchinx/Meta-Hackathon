import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './http'
import { fetchMe, login, register } from './auth'

afterEach(() => vi.unstubAllGlobals())

const user = { id: 'u1', email: 'ana@example.com', displayName: 'Ana', emailVerified: false }

describe('auth api', () => {
  it('reads the current user, null when anonymous', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ user: null }), { status: 200 })))
    await expect(fetchMe()).resolves.toBeNull()
  })

  it('registers with a JSON body and returns the user', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ user }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(register('ana@example.com', 'Ana', 'una clave larga')).resolves.toEqual(user)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/auth/register')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(init.body)).toEqual({ email: 'ana@example.com', displayName: 'Ana', password: 'una clave larga' })
  })

  it('surfaces the backend message on bad credentials', async () => {
    const body = { code: 'BAD_CREDENTIALS', message: 'Correo o contraseña incorrectos.' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status: 401 })))

    const error = await login('ana@example.com', 'x').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('BAD_CREDENTIALS')
    expect((error as ApiError).message).toBe('Correo o contraseña incorrectos.')
  })
})
