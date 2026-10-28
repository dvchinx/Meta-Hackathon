import { useEffect, useRef, useState } from 'react'
import { fetchAuthConfig, signInWithGoogle, type User } from '../api/auth'
import { messageOf } from '../api/http'
import { Notice } from '../ui/Notice'

const SCRIPT_URL = 'https://accounts.google.com/gsi/client'
let scriptPromise: Promise<void> | null = null

function loadGoogleScript(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve()
  scriptPromise ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = SCRIPT_URL
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => {
      scriptPromise = null
      reject(new Error('Google script failed to load'))
    }
    document.head.append(script)
  })
  return scriptPromise
}

/**
 * Google's own button (their brand rules require it), rendered only when the server has a client id. Followed by a
 * divider so the email form below reads as the alternative.
 */
export function GoogleButton({ onSignedIn }: { onSignedIn: (user: User) => void }) {
  const container = useRef<HTMLDivElement>(null)
  const onSignedInRef = useRef(onSignedIn)
  const [clientId, setClientId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    onSignedInRef.current = onSignedIn
  }, [onSignedIn])

  useEffect(() => {
    let cancelled = false
    void fetchAuthConfig().then(({ googleClientId }) => {
      if (!cancelled) setClientId(googleClientId)
    })
    return () => {
      cancelled = true
    }
  }, [])

  // Runs once the wrapper is visible, so the button can be sized to the form's width.
  useEffect(() => {
    if (!clientId) return
    let cancelled = false
    loadGoogleScript()
      .then(() => {
        if (cancelled || !container.current || !window.google) return
        window.google.accounts.id.initialize({
          client_id: clientId,
          ux_mode: 'popup',
          auto_select: false,
          callback: ({ credential }) => {
            setError(null)
            signInWithGoogle(credential)
              .then((user) => onSignedInRef.current(user))
              .catch((e: unknown) => setError(messageOf(e)))
          },
        })
        window.google.accounts.id.renderButton(container.current, {
          type: 'standard',
          theme: 'outline',
          size: 'large',
          text: 'continue_with',
          shape: 'rectangular',
          logo_alignment: 'center',
          // Google accepts 200–400 px.
          width: Math.min(400, Math.max(200, Math.floor(container.current.clientWidth))),
          locale: 'es',
        })
      })
      .catch(() => {
        // Google unreachable (blocked script, offline): the email form still works.
        if (!cancelled) setClientId(null)
      })
    return () => {
      cancelled = true
    }
  }, [clientId])

  if (!clientId) return null

  return (
    <div className="google-signin">
      <div ref={container} className="google-button" />
      {error && <Notice tone="error">{error}</Notice>}
      <div className="divider">
        <span>o con tu correo</span>
      </div>
    </div>
  )
}
