import { useState } from 'react'
import { confirmPasswordReset } from '../api/auth'
import { messageOf } from '../api/http'
import { href } from '../routing'
import { Field } from '../ui/Field'
import { Notice } from '../ui/Notice'
import { AuthPage } from './AuthPage'

const MIN_PASSWORD = 8

export function ResetPasswordPage({ token }: { token: string | null }) {
  const [password, setPassword] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!token) return
    setBusy(true)
    setError(null)
    try {
      await confirmPasswordReset(token, password)
      setDone(true)
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

  if (!token) {
    return (
      <AuthPage title="Enlace incompleto">
        <Notice tone="error">Este enlace no tiene el código de recuperación. Pide uno nuevo.</Notice>
        <a className="button button-secondary button-block" href={href('/recuperar')}>
          Pedir un enlace nuevo
        </a>
      </AuthPage>
    )
  }

  if (done) {
    return (
      <AuthPage title="Contraseña cambiada">
        <p className="auth-intro">Cerramos tus sesiones anteriores. Entra con la contraseña nueva.</p>
        <a className="button button-primary button-lg button-block" href={href('/entrar')}>
          Entrar
        </a>
      </AuthPage>
    )
  }

  return (
    <AuthPage title="Elige una contraseña nueva">
      <form className="stack" onSubmit={submit} noValidate>
        <Field
          label="Contraseña nueva"
          type="password"
          autoComplete="new-password"
          required
          minLength={MIN_PASSWORD}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          hint={`Mínimo ${MIN_PASSWORD} caracteres.`}
        />
        {error && <Notice tone="error">{error}</Notice>}
        <button type="submit" className="button button-primary button-lg button-block" disabled={busy || password.length < MIN_PASSWORD}>
          {busy ? 'Guardando…' : 'Guardar contraseña'}
        </button>
      </form>
    </AuthPage>
  )
}
