import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AuthState } from './context'
import { RequireAccount } from './RequireAccount'

const auth = vi.hoisted(() => ({ state: { status: 'loading' } as AuthState }))
vi.mock('./useAuth', () => ({ useAuth: () => ({ state: auth.state, setUser: vi.fn(), refresh: vi.fn(), signOut: vi.fn() }) }))

afterEach(cleanup)

describe('RequireAccount', () => {
  it('asks anonymous visitors to sign up or in, keeping where they were going', () => {
    auth.state = { status: 'anonymous' }
    render(
      <RequireAccount next="/reportar">
        <p>formulario</p>
      </RequireAccount>,
    )

    expect(screen.queryByText('formulario')).toBeNull()
    expect((screen.getByText('Crear cuenta') as HTMLAnchorElement).getAttribute('href')).toBe('#/registro?next=%2Freportar')
    expect((screen.getByText('Ya tengo cuenta') as HTMLAnchorElement).getAttribute('href')).toBe('#/entrar?next=%2Freportar')
  })

  it('reminds unverified accounts to confirm the email', () => {
    auth.state = { status: 'signedIn', user: { id: 'u', email: 'ana@example.com', displayName: 'Ana', emailVerified: false } }
    render(
      <RequireAccount next="/reportar">
        <p>formulario</p>
      </RequireAccount>,
    )

    expect(screen.queryByText('formulario')).toBeNull()
    expect(screen.getByText('ana@example.com')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Enviar el correo de nuevo' })).toBeTruthy()
  })

  it('lets verified accounts through', () => {
    auth.state = { status: 'signedIn', user: { id: 'u', email: 'ana@example.com', displayName: 'Ana', emailVerified: true } }
    render(
      <RequireAccount next="/reportar">
        <p>formulario</p>
      </RequireAccount>,
    )

    expect(screen.getByText('formulario')).toBeTruthy()
  })
})
