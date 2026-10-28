import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const user = { id: 'u1', email: 'ana@gmail.com', displayName: 'Ana', emailVerified: true }

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), { status: 200 })
}

beforeEach(() => {
  vi.resetModules()
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  delete window.google
})

describe('GoogleButton', () => {
  it('stays hidden when the server has no Google client id', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ googleClientId: null }))
    vi.stubGlobal('fetch', fetchMock)
    const { GoogleButton } = await import('./GoogleButton')

    const { container } = render(<GoogleButton onSignedIn={() => {}} />)

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/auth/config', expect.anything()))
    expect(container.firstChild).toBeNull()
  })

  it('renders Google\'s button and signs in with the credential it returns', async () => {
    const initialize = vi.fn()
    const renderButton = vi.fn()
    window.google = { accounts: { id: { initialize, renderButton } } }
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ googleClientId: 'client-123.apps.googleusercontent.com' }))
      .mockResolvedValueOnce(jsonResponse({ user }))
    vi.stubGlobal('fetch', fetchMock)
    const onSignedIn = vi.fn()
    const { GoogleButton } = await import('./GoogleButton')

    render(<GoogleButton onSignedIn={onSignedIn} />)

    await waitFor(() => expect(renderButton).toHaveBeenCalled())
    expect(initialize.mock.calls[0][0].client_id).toBe('client-123.apps.googleusercontent.com')
    expect(renderButton.mock.calls[0][1]).toMatchObject({ text: 'continue_with', locale: 'es' })
    expect(screen.getByText('o con tu correo')).toBeTruthy()

    initialize.mock.calls[0][0].callback({ credential: 'id-token-from-google' })

    await waitFor(() => expect(onSignedIn).toHaveBeenCalledWith(user))
    const [url, init] = fetchMock.mock.calls[1]
    expect(url).toBe('/api/auth/google')
    expect(JSON.parse(init.body)).toEqual({ credential: 'id-token-from-google' })
  })
})
