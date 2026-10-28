import { useState } from 'react'
import { login } from '../api/auth'
import { messageOf } from '../api/http'
import { href, navigate, safeNext } from '../routing'
import { Field } from '../ui/Field'
import { Notice } from '../ui/Notice'
import { AuthPage } from './AuthPage'
import { GoogleButton } from './GoogleButton'
import { useAuth } from './useAuth'

export function LoginPage({ next }: { next: string | null }) {
  const { setUser } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const destination = safeNext(next)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      setUser(await login(email, password))
      navigate(destination)
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthPage
      title="Entrar"
      intro="Con tu cuenta puedes reportar daños y seguir lo que pasa con cada uno."
      footer={
        <>
          ¿No tienes cuenta? <a href={href('/registro', next ? { next: destination } : undefined)}>Crear una cuenta</a>
        </>
      }
    >
      <GoogleButton
        onSignedIn={(user) => {
          setUser(user)
          navigate(destination)
        }}
      />
      <form className="stack" onSubmit={submit} noValidate>
        <Field
          label="Correo"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Field
          label="Contraseña"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          hint={<a href={href('/recuperar')}>¿Olvidaste tu contraseña?</a>}
        />
        {error && <Notice tone="error">{error}</Notice>}
        <button type="submit" className="button button-primary button-lg button-block" disabled={busy || !email || !password}>
          {busy ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </AuthPage>
  )
}
