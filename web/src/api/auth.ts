import { parseJson, postJson } from './http'

export interface User {
  id: string
  email: string
  displayName: string
  emailVerified: boolean
}

interface MeResponse {
  user: User | null
}

export async function fetchMe(signal?: AbortSignal): Promise<User | null> {
  const response = await fetch('/api/auth/me', { signal, headers: { Accept: 'application/json' } })
  return (await parseJson<MeResponse>(response)).user
}

export async function register(email: string, displayName: string, password: string): Promise<User> {
  return (await parseJson<MeResponse>(await postJson('/api/auth/register', { email, displayName, password }))).user!
}

export async function login(email: string, password: string): Promise<User> {
  return (await parseJson<MeResponse>(await postJson('/api/auth/login', { email, password }))).user!
}

export async function logout(): Promise<void> {
  await parseJson<void>(await postJson('/api/auth/logout'))
}

export async function verifyEmail(token: string): Promise<User> {
  return (await parseJson<MeResponse>(await postJson('/api/auth/verify-email', { token }))).user!
}

export async function resendVerification(): Promise<void> {
  await parseJson<void>(await postJson('/api/auth/resend-verification'))
}

export async function requestPasswordReset(email: string): Promise<void> {
  await parseJson<void>(await postJson('/api/auth/password-reset', { email }))
}

export async function confirmPasswordReset(token: string, password: string): Promise<void> {
  await parseJson<void>(await postJson('/api/auth/password-reset/confirm', { token, password }))
}

export interface AuthConfig {
  /** Null when Google sign-in is not configured on the server. */
  googleClientId: string | null
}

let configPromise: Promise<AuthConfig> | null = null

/** Fetched once per page load. */
export function fetchAuthConfig(): Promise<AuthConfig> {
  configPromise ??= fetch('/api/auth/config', { headers: { Accept: 'application/json' } })
    .then((r) => parseJson<AuthConfig>(r))
    .catch(() => {
      configPromise = null
      return { googleClientId: null }
    })
  return configPromise
}

export async function signInWithGoogle(credential: string): Promise<User> {
  return (await parseJson<MeResponse>(await postJson('/api/auth/google', { credential }))).user!
}
