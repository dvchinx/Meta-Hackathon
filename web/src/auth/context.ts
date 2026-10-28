import { createContext } from 'react'
import type { User } from '../api/auth'

export type AuthState = { status: 'loading' } | { status: 'anonymous' } | { status: 'signedIn'; user: User }

export interface AuthContextValue {
  state: AuthState
  /** Replaces the signed-in user after login, registration or verification. */
  setUser: (user: User | null) => void
  refresh: () => Promise<void>
  signOut: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
