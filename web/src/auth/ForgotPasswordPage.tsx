import { useState } from 'react'
import { requestPasswordReset } from '../api/auth'
import { messageOf } from '../api/http'
import { href } from '../routing'
import { Field } from '../ui/Field'
import { Notice } from '../ui/Notice'
import { AuthPage } from './AuthPage'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await requestPasswordReset(email)
      setSent(true)
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

  if (sent) {
    return (
      <AuthPage title="Revisa tu correo">
        <p className="auth-intro">
          Si existe una cuenta con <strong>{email}</strong>, le enviamos un enlace para elegir una contraseña nueva.
          Vence en 1 hora.
        </p>
        <a className="button button-secondary button-block" href={href('/entrar')}>
          Volver a entrar
        </a>
      </AuthPage>
    )
  }

  return (
    <AuthPage
      title="Recuperar contraseña"
      intro="Te enviamos un enlace al correo para que elijas una contraseña nueva."
      footer={<a href={href('/entrar')}>Volver a entrar</a>}
    >
      <form className="stack" onSubmit={submit} noValidate>
        <Field label="Correo" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        {error && <Notice tone="error">{error}</Notice>}
        <button type="submit" className="button button-primary button-lg button-block" disabled={busy || !email}>
          {busy ? 'Enviando…' : 'Enviar enlace'}
        </button>
      </form>
    </AuthPage>
  )
}
