import { useState, type ReactNode } from 'react'
import { resendVerification } from '../api/auth'
import { messageOf } from '../api/http'
import { href } from '../routing'
import { Icon } from '../ui/Icon'
import { Notice } from '../ui/Notice'
import { useAuth } from './useAuth'

/**
 * Wraps screens that need a verified account. Anonymous visitors get a short explanation and the two ways in;
 * unverified accounts get a reminder with a resend button.
 */
export function RequireAccount({ next, children }: { next: string; children: ReactNode }) {
  const { state } = useAuth()
  const [resent, setResent] = useState<string | null>(null)

  if (state.status === 'loading') {
    return (
      <div className="page">
        <div className="spinner" aria-label="Cargando" />
      </div>
    )
  }

  if (state.status === 'anonymous') {
    return (
      <div className="page page-narrow">
        <div className="card status-screen">
          <span className="status-icon info">
            <Icon name="user" size={28} />
          </span>
          <h1>Necesitas una cuenta para reportar</h1>
          <p>Es rápido: un nombre, un correo y una contraseña. Así puedes seguir tus reportes y confirmar reparaciones.</p>
          <div className="status-actions">
            <a className="button button-primary" href={href('/registro', { next })}>
              Crear cuenta
            </a>
            <a className="button button-secondary" href={href('/entrar', { next })}>
              Ya tengo cuenta
            </a>
          </div>
        </div>
      </div>
    )
  }

  if (!state.user.emailVerified) {
    return (
      <div className="page page-narrow">
        <div className="card status-screen">
          <span className="status-icon info">
            <Icon name="mail" size={28} />
          </span>
          <h1>Confirma tu correo</h1>
          <p>
            Te enviamos un enlace a <strong>{state.user.email}</strong>. Ábrelo para poder reportar.
          </p>
          {resent && <Notice tone="info">{resent}</Notice>}
          <div className="status-actions">
            <button
              type="button"
              className="button button-secondary"
              onClick={() =>
                resendVerification()
                  .then(() => setResent('Enviamos un correo nuevo. Revisa también la carpeta de spam.'))
                  .catch((e: unknown) => setResent(messageOf(e)))
              }
            >
              Enviar el correo de nuevo
            </button>
          </div>
        </div>
      </div>
    )
  }

  return <>{children}</>
}
