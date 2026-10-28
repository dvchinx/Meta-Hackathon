import { useEffect, useState } from 'react'
import { resendVerification, verifyEmail } from '../api/auth'
import { messageOf } from '../api/http'
import { href, safeNext } from '../routing'
import { Icon } from '../ui/Icon'
import { Notice } from '../ui/Notice'
import { AuthPage } from './AuthPage'
import { useAuth } from './useAuth'

type Status = { kind: 'waiting' } | { kind: 'verifying' } | { kind: 'verified' } | { kind: 'failed'; message: string }

/** With a token in the URL it confirms the address; without one it tells the citizen to check the inbox. */
export function VerifyEmailPage({ token, next }: { token: string | null; next: string | null }) {
  const { state, setUser } = useAuth()
  const [status, setStatus] = useState<Status>(token ? { kind: 'verifying' } : { kind: 'waiting' })
  const [resent, setResent] = useState<string | null>(null)
  const destination = safeNext(next)

  useEffect(() => {
    if (!token) return
    let cancelled = false
    verifyEmail(token)
      .then((user) => {
        if (cancelled) return
        setUser(user)
        setStatus({ kind: 'verified' })
      })
      .catch((e: unknown) => {
        if (!cancelled) setStatus({ kind: 'failed', message: messageOf(e) })
      })
    return () => {
      cancelled = true
    }
  }, [token, setUser])

  async function resend() {
    try {
      await resendVerification()
      setResent('Enviamos un correo nuevo. Revisa también la carpeta de spam.')
    } catch (e) {
      setResent(messageOf(e))
    }
  }

  if (status.kind === 'verifying') {
    return (
      <AuthPage title="Confirmando tu correo">
        <div className="spinner" aria-label="Cargando" />
      </AuthPage>
    )
  }

  if (status.kind === 'verified') {
    return (
      <AuthPage title="Correo confirmado">
        <p className="auth-intro">Ya puedes reportar daños y seguir lo que pasa con cada uno.</p>
        <a className="button button-primary button-lg button-block" href={href(destination === '/' ? '/reportar' : destination)}>
          {destination === '/' ? 'Reportar un daño' : 'Continuar'}
        </a>
      </AuthPage>
    )
  }

  const email = state.status === 'signedIn' ? state.user.email : null
  return (
    <AuthPage title="Confirma tu correo">
      {status.kind === 'failed' && <Notice tone="error">{status.message}</Notice>}
      <p className="auth-intro">
        {email ? (
          <>
            Te enviamos un enlace a <strong>{email}</strong>. Ábrelo para confirmar tu cuenta y empezar a reportar.
          </>
        ) : (
          'Te enviamos un enlace al correo con el que creaste la cuenta. Ábrelo para confirmar tu cuenta.'
        )}
      </p>
      <p className="muted">
        <Icon name="mail" size={16} /> El enlace vence en 24 horas. Si no llega, revisa la carpeta de spam.
      </p>
      {resent && <Notice tone="info">{resent}</Notice>}
      {email && (
        <button type="button" className="button button-secondary button-block" onClick={resend}>
          Enviar el correo de nuevo
        </button>
      )}
    </AuthPage>
  )
}
