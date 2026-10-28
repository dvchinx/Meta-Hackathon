import { useState } from 'react'
import { register } from '../api/auth'
import { messageOf } from '../api/http'
import { href, navigate, safeNext } from '../routing'
import { Field } from '../ui/Field'
import { Icon } from '../ui/Icon'
import { Notice } from '../ui/Notice'
import { AuthPage } from './AuthPage'
import { GoogleButton } from './GoogleButton'
import { useAuth } from './useAuth'

const MIN_PASSWORD = 8

export function RegisterPage({ next }: { next: string | null }) {
  const { setUser } = useAuth()
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const destination = safeNext(next)
  const passwordTooShort = password.length > 0 && password.length < MIN_PASSWORD

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      setUser(await register(email, displayName, password))
      navigate('/verificar', { next: destination })
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthPage
      title="Crear cuenta"
      intro="Entra con Google o con tu correo. Para reportar no necesitas cédula ni dirección."
      footer={
        <>
          ¿Ya tienes cuenta? <a href={href('/entrar', next ? { next: destination } : undefined)}>Entrar</a>
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
          label="Nombre"
          autoComplete="name"
          required
          maxLength={60}
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          hint="Como quieres que te llamemos. No se publica."
        />
        <Field label="Correo" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <Field
          label="Contraseña"
          type="password"
          autoComplete="new-password"
          required
          minLength={MIN_PASSWORD}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={passwordTooShort ? `Mínimo ${MIN_PASSWORD} caracteres.` : null}
          hint={`Mínimo ${MIN_PASSWORD} caracteres. Una frase es más fácil de recordar.`}
        />
        {error && <Notice tone="error">{error}</Notice>}
        <button
          type="submit"
          className="button button-primary button-lg button-block"
          disabled={busy || !email || !displayName || password.length < MIN_PASSWORD}
        >
          {busy ? 'Creando cuenta…' : 'Crear cuenta'}
        </button>
        <p className="privacy">
          <Icon name="shield" size={16} />
          <span>
            Tu correo solo se usa para entrar y avisarte de tus reportes. Nunca se publica. Al crear la cuenta aceptas
            la <a href={href('/privacidad')}>Política de tratamiento de datos</a>.
          </span>
        </p>
      </form>
    </AuthPage>
  )
}
