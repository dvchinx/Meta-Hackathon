import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { fetchMe, logout as apiLogout, type User } from '../api/auth'
import { AuthContext, type AuthState } from './context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  const setUser = useCallback((user: User | null) => {
    setState(user ? { status: 'signedIn', user } : { status: 'anonymous' })
  }, [])

  const refresh = useCallback(
    () =>
      fetchMe()
        .then(setUser)
        .catch(() => setState({ status: 'anonymous' })),
    [setUser],
  )

  const signOut = useCallback(async () => {
    try {
      await apiLogout()
    } finally {
      setState({ status: 'anonymous' })
    }
  }, [])

  // The session cookie is the external system: ask the backend who is signed in.
  useEffect(() => {
    const controller = new AbortController()
    fetchMe(controller.signal)
      .then(setUser)
      .catch(() => {
        if (!controller.signal.aborted) setState({ status: 'anonymous' })
      })
    return () => controller.abort()
  }, [setUser])

  const value = useMemo(() => ({ state, setUser, refresh, signOut }), [state, setUser, refresh, signOut])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
