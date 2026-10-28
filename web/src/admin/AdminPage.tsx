import { useCallback, useEffect, useId, useState } from 'react'
import { fetchOverview, registerAgencyResponse, type AdminOverview, type AgencyResponseResult } from '../api/admin'
import { ApiError, messageOf } from '../api/http'
import { fetchIssues } from '../api/issues'
import { DAMAGE_LABELS, STATUS_LABELS, deadlineLabel } from '../issues/labels'
import type { IssueStatus, IssueSummary } from '../issues/types'
import { href } from '../routing'
import { Field } from '../ui/Field'
import { Icon } from '../ui/Icon'
import { Notice } from '../ui/Notice'

const TOKEN_KEY = 'civic-admin-token'

/** Ready-made agency replies, to run the demo in seconds. Muse Spark still has to read them. */
const PRESETS = [
  {
    label: 'Dice que lo reparó',
    body: 'Respetado ciudadano: le informamos que el día de ayer una cuadrilla realizó la intervención solicitada y el daño quedó reparado. Cordialmente, Oficina de Atención al Ciudadano.',
  },
  {
    label: 'Evasiva',
    body: 'Respetado ciudadano: su solicitud fue incluida en el plan de mantenimiento y será atendida de acuerdo con la disponibilidad presupuestal y la priorización técnica de la entidad.',
  },
  {
    label: 'Acuse de recibo',
    body: 'Hemos recibido su solicitud y fue radicada con éxito. Pronto recibirá respuesta.',
  },
]

const money = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'USD', minimumFractionDigits: 2 })
const shortDate = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

function storedToken(): string {
  try {
    return sessionStorage.getItem(TOKEN_KEY) ?? ''
  } catch {
    return ''
  }
}

function storeToken(token: string | null) {
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token)
    else sessionStorage.removeItem(TOKEN_KEY)
  } catch {
    // Private mode: the token just is not remembered.
  }
}

/** Admin panel: simulated agency replies, credits spent and the manual review queue. Needs ADMIN_TOKEN. */
export function AdminPage() {
  const [token, setToken] = useState(storedToken)
  const [overview, setOverview] = useState<AdminOverview | null>(null)
  const [issues, setIssues] = useState<IssueSummary[]>([])
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(
    (current: string) => {
      if (!current) return
      Promise.all([fetchOverview(current), fetchIssues()])
        .then(([o, all]) => {
          setOverview(o)
          setIssues(all)
          setError(null)
          storeToken(current)
        })
        .catch((e: unknown) => {
          if (e instanceof ApiError && (e.status === 401 || e.status === 404)) {
            storeToken(null)
            setToken('')
          }
          setError(messageOf(e))
        })
    },
    [],
  )

  useEffect(() => {
    // Only the token remembered from a previous visit; a typed one is loaded on submit.
    const remembered = storedToken()
    if (remembered) load(remembered)
  }, [load])

  if (!overview) {
    return <TokenForm initial={token} error={error} onSubmit={(t) => { setToken(t); load(t) }} />
  }

  const filed = issues.filter((i) => i.status === 'FILED')
  const spentShare = Math.min(100, (overview.spentUsd / overview.budgetUsd) * 100)

  return (
    <div className="page page-wide">
      <header className="page-header">
        <h1>Panel de administración</h1>
        <p>Las entidades se simulan: aquí escribes su respuesta a cada petición. La foto ciudadana sigue siendo la única que cierra un caso.</p>
        <div className="form-actions">
          <button type="button" className="button button-secondary button-sm" onClick={() => load(token)}>
            <Icon name="refresh" size={16} />
            Actualizar
          </button>
          <button
            type="button"
            className="button button-ghost button-sm"
            onClick={() => {
              storeToken(null)
              setToken('')
              setOverview(null)
            }}
          >
            Salir
          </button>
        </div>
      </header>

      <div className="admin-grid">
        <section className="card admin-card">
          <h2 className="section-title">Créditos de Meta</h2>
          <p className="admin-spend">
            {money.format(overview.spentUsd)} <span className="muted">de {money.format(overview.budgetUsd)}</span>
          </p>
          <div className="meter" role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(spentShare)} aria-label="Gasto del presupuesto">
            <span style={{ width: `${spentShare}%` }} />
          </div>
          <p className="muted">
            {overview.metaEnabled ? 'Modelos reales activos (META_API_ENABLED=true).' : 'Modelos simulados: no se gastan créditos.'}
          </p>
        </section>

        <section className="card admin-card">
          <h2 className="section-title">Daños por estado</h2>
          <ul className="legend">
            {(Object.keys(STATUS_LABELS) as IssueStatus[]).map((status) => (
              <li key={status}>
                {STATUS_LABELS[status]}
                <span className="count">{overview.issuesByStatus[status] ?? 0}</span>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <section className="stack admin-section">
        <h2>Peticiones esperando respuesta ({filed.length})</h2>
        {filed.length === 0 && <p className="muted">No hay peticiones radicadas sin respuesta.</p>}
        {filed.map((issue) => (
          <AgencyReplyForm key={issue.id} token={token} issue={issue} onDone={() => load(token)} />
        ))}
      </section>

      <section className="stack admin-section">
        <h2>Revisión manual ({overview.manualReview.length})</h2>
        {overview.manualReview.length === 0 ? (
          <p className="muted">Nada pendiente.</p>
        ) : (
          <ul className="review-list">
            {overview.manualReview.map((r) => (
              <li key={r.reportId} className="card">
                <strong>{r.damageType ? (DAMAGE_LABELS[r.damageType as keyof typeof DAMAGE_LABELS] ?? r.damageType) : 'Sin clasificar'}</strong>
                <span className="muted">
                  {' '}
                  · {r.channel === 'WHATSAPP' ? 'WhatsApp' : 'Web'} · {shortDate.format(new Date(r.createdAt))}
                </span>
                {r.summary && <p>{r.summary}</p>}
                {r.reason && <p className="muted">Motivo: {r.reason}</p>}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

function TokenForm({ initial, error, onSubmit }: { initial: string; error: string | null; onSubmit: (token: string) => void }) {
  const [value, setValue] = useState(initial)
  return (
    <div className="page page-narrow">
      <form
        className="card auth-card"
        onSubmit={(e) => {
          e.preventDefault()
          if (value.trim()) onSubmit(value.trim())
        }}
      >
        <h1>Panel de administración</h1>
        <Field
          label="Código de administración"
          type="password"
          autoComplete="off"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          hint="Es el ADMIN_TOKEN del servidor. Se recuerda solo en esta pestaña."
        />
        {error && <Notice tone="error">{error}</Notice>}
        <button type="submit" className="button button-primary button-block" disabled={!value.trim()}>
          Entrar
        </button>
      </form>
    </div>
  )
}

const CLASSIFICATION_LABELS = { RESOLVES: 'Dice que lo reparó', EVASIVE: 'Evasiva', NO_RESPONSE: 'Sin respuesta de fondo' }

function AgencyReplyForm({ token, issue, onDone }: { token: string; issue: IssueSummary; onDone: () => void }) {
  const [body, setBody] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<AgencyResponseResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const textId = useId()

  async function submit() {
    setBusy(true)
    setError(null)
    try {
      setResult(await registerAgencyResponse(token, issue.id, body))
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className="card admin-reply">
      <div className="admin-reply-head">
        {issue.photoUrl && <img src={issue.photoUrl} alt="" />}
        <div>
          <a href={href('/dano', { id: issue.id })}>
            <strong>
              {DAMAGE_LABELS[issue.damageType]}
              {issue.locality && ` en ${issue.locality}`}
            </strong>
          </a>
          <p className="muted">
            {issue.agency} · Radicado {issue.petition?.radicado}
          </p>
          {issue.petition && <p className="muted">{deadlineLabel(issue.petition)}</p>}
        </div>
      </div>
      {result ? (
        <div className="stack">
          <Notice tone="info">
            Muse Spark la leyó como <strong>{CLASSIFICATION_LABELS[result.classification]}</strong>: {result.summary} Nuevo estado:{' '}
            {STATUS_LABELS[result.status]}.
          </Notice>
          <button type="button" className="button button-secondary button-sm button-inline" onClick={onDone}>
            Listo
          </button>
        </div>
      ) : (
        <div className="stack">
          <div className="chips">
            {PRESETS.map((p) => (
              <button key={p.label} type="button" className="chip" onClick={() => setBody(p.body)}>
                {p.label}
              </button>
            ))}
          </div>
          <label htmlFor={textId} className="field-label">
            Respuesta de la entidad
          </label>
          <textarea id={textId} className="textarea" value={body} maxLength={4000} onChange={(e) => setBody(e.target.value)} />
          {error && <Notice tone="error">{error}</Notice>}
          <button type="button" className="button button-primary button-inline" disabled={busy || !body.trim()} onClick={() => void submit()}>
            {busy ? 'Leyendo la respuesta…' : 'Registrar respuesta'}
          </button>
        </div>
      )}
    </article>
  )
}
