import { useCallback, useEffect, useId, useState } from 'react'
import { messageOf } from '../api/http'
import { fetchIssue, fetchIssueHistory, verifyRepair } from '../api/issues'
import { endorseIssue } from '../api/reports'
import { useAuth } from '../auth/useAuth'
import { DAMAGE_LABELS, STATUS_COLORS, STATUS_LABELS, canFile, deadlineLabel, reportsLabel } from '../issues/labels'
import type { IssueHistory, IssueStatus, IssueSummary, VerificationReceipt } from '../issues/types'
import { OfficialChannelLink } from '../petition/OfficialChannelLink'
import { href } from '../routing'
import { DamageIcon } from '../ui/DamageIcon'
import { Icon } from '../ui/Icon'
import { Notice } from '../ui/Notice'

const dateTime = new Intl.DateTimeFormat('es-CO', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
  timeZone: 'America/Bogota',
})
const date = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'America/Bogota' })

const STEP_LABELS: Record<IssueStatus, string> = {
  REPORTED: 'Primer reporte ciudadano',
  FILED: 'Se radicó el derecho de petición',
  AGENCY_RESPONDED: 'La entidad respondió',
  REPAIR_CLAIMED: 'La entidad dice que lo reparó',
  NEEDS_MORE_EVIDENCE: 'La foto no fue concluyente',
  VERIFIED: 'Una foto confirmó la reparación',
  REOPENED: 'Una foto mostró que sigue dañado',
}

const CLASSIFICATION_LABELS = { RESOLVES: 'Dice que lo reparó', EVASIVE: 'Respuesta evasiva', NO_RESPONSE: 'Solo acusó recibo' }

const OUTCOME_MESSAGES: Record<VerificationReceipt['outcome'], { tone: 'info' | 'warning' | 'error'; text: string }> = {
  REPAIRED: { tone: 'info', text: '¡Gracias! Tu foto confirma la reparación: el daño queda como reparado verificado.' },
  NOT_REPAIRED: { tone: 'error', text: 'Tu foto muestra que el daño sigue ahí. Lo reabrimos para volver a exigir la reparación.' },
  INCONCLUSIVE: {
    tone: 'warning',
    text: 'No pudimos confirmarlo con esta foto. Prueba con otra desde un ángulo parecido al original y con buena luz.',
  },
}

type State =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; issue: IssueSummary; history: IssueHistory }

/** Everything about one damage, shareable by link: before and after, who is responsible and every step so far. */
export function IssueDetailPage({ issueId }: { issueId: string | null }) {
  const [state, setState] = useState<State>({ kind: 'loading' })
  const [copied, setCopied] = useState(false)

  const load = useCallback(
    (signal?: AbortSignal) => {
      if (!issueId) return
      Promise.all([fetchIssue(issueId, signal), fetchIssueHistory(issueId, signal)])
        .then(([issue, history]) => setState({ kind: 'ready', issue, history }))
        .catch((e: unknown) => {
          if (!signal?.aborted) setState({ kind: 'error', message: messageOf(e) })
        })
    },
    [issueId],
  )

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal)
    return () => controller.abort()
  }, [load])

  const back = (
    <a className="back-link" href={href('/')}>
      <Icon name="arrowLeft" size={16} />
      Volver al mapa
    </a>
  )

  if (!issueId || state.kind === 'error') {
    return (
      <div className="page">
        <header className="page-header">{back}</header>
        <Notice tone="error">{state.kind === 'error' ? state.message : 'No encontramos ese daño.'}</Notice>
      </div>
    )
  }
  if (state.kind === 'loading') {
    return (
      <div className="page">
        <div className="spinner" aria-label="Cargando" />
      </div>
    )
  }

  const { issue, history } = state
  const after = history.verifications.at(-1)
  const title = `${DAMAGE_LABELS[issue.damageType]}${issue.locality ? ` en ${issue.locality}` : ''}`
  const awaitingPhoto = issue.status === 'REPAIR_CLAIMED' || issue.status === 'NEEDS_MORE_EVIDENCE'

  async function share() {
    const url = window.location.href
    if (navigator.share) {
      await navigator.share({ title, url }).catch(() => undefined)
      return
    }
    await navigator.clipboard?.writeText(url)
    setCopied(true)
  }

  return (
    <div className="page page-wide">
      <header className="page-header">
        {back}
        <div className="detail-title">
          <span className="detail-icon" style={{ background: STATUS_COLORS[issue.status] }}>
            <DamageIcon type={issue.damageType} size={22} decorative />
          </span>
          <div>
            <h1>{title}</h1>
            <p className="issue-popup-status">
              <span className="dot" style={{ background: STATUS_COLORS[issue.status] }} />
              {STATUS_LABELS[issue.status]}
              <span className="muted"> · {reportsLabel(issue.reportCount)}</span>
              {issue.example && <span className="badge badge-example">Ejemplo</span>}
            </p>
          </div>
        </div>
      </header>

      <div className="detail-grid">
        <div className="stack">
          <section className={after ? 'before-after has-after' : 'before-after'} aria-label="Fotos">
            <figure>
              {issue.photoUrl ? (
                <img src={issue.photoUrl} alt={`Foto del reporte: ${title}`} />
              ) : (
                <div className="photo-missing">Sin foto</div>
              )}
              <figcaption>Antes · {date.format(new Date(issue.openedAt))}</figcaption>
            </figure>
            {after && (
              <figure>
                <img src={after.photoUrl} alt="Después: foto de verificación más reciente" />
                <figcaption>Después · {date.format(new Date(after.at))}</figcaption>
              </figure>
            )}
          </section>

          {issue.example && (
            <Notice>
              <strong>Daño de ejemplo para la demostración.</strong> Sus fotos se editaron con IA sobre una foto real de
              la calle y su historial está simulado; los reportes ciudadanos no llevan esta etiqueta.
              {issue.example.credit && <span className="example-credit">{issue.example.credit}</span>}
            </Notice>
          )}

          {awaitingPhoto && <VerifyPanel issue={issue} onVerified={() => load()} />}

          <section className="card timeline-card">
            <h2 className="section-title">Línea de tiempo</h2>
            <Timeline issue={issue} history={history} />
          </section>
        </div>

        <aside className="stack">
          <div className="card detail-facts">
            <dl className="facts">
              <div className="wide">
                <dt>Entidad responsable</dt>
                <dd>{issue.agency ?? 'En revisión'}</dd>
              </div>
              {issue.locality && (
                <div>
                  <dt>Localidad</dt>
                  <dd>{issue.locality}</dd>
                </div>
              )}
              <div>
                <dt>Reportado</dt>
                <dd>{date.format(new Date(issue.openedAt))}</dd>
              </div>
              {issue.petition && (
                <div className="wide">
                  <dt>Radicado (simulado)</dt>
                  <dd>
                    {issue.petition.radicado}
                    {issue.status === 'FILED' && (
                      <span className={issue.petition.overdue ? 'deadline is-overdue' : 'deadline'}>
                        {deadlineLabel(issue.petition)}
                      </span>
                    )}
                  </dd>
                </div>
              )}
            </dl>
            <div className="detail-actions">
              {canFile(issue) && (
                <a className="button button-primary" href={href('/radicar', { issue: issue.id })}>
                  <Icon name="mail" size={18} />
                  Radicar petición
                </a>
              )}
              {issue.status !== 'VERIFIED' && <EndorseButton issue={issue} />}
              <button type="button" className="button button-ghost" onClick={() => void share()}>
                <Icon name="share" size={18} />
                {copied ? 'Enlace copiado' : 'Compartir'}
              </button>
            </div>
            {issue.officialChannel && <OfficialChannelLink channel={issue.officialChannel} />}
          </div>
          <p className="rule">
            <Icon name="shield" size={18} />
            <span>
              <strong>Solo una foto nueva cierra un caso.</strong> Si la entidad dice que lo reparó, pedimos a los
              ciudadanos una foto actual del lugar y la comparamos con la original.
            </span>
          </p>
        </aside>
      </div>
    </div>
  )
}

function Timeline({ issue, history }: { issue: IssueSummary; history: IssueHistory }) {
  // Agency replies and photo checks line up, in order, with the steps they caused.
  let response = 0
  let check = 0
  return (
    <ol className="timeline">
      {history.steps.map((step, index) => {
        const reply = step.cause === 'AGENCY_RESPONSE' && step.to === 'AGENCY_RESPONDED' ? history.agencyResponses[response++] : undefined
        const photo = step.cause === 'PHOTO_VERIFICATION' ? history.verifications[check++] : undefined
        return (
          <li key={index}>
            <span className="timeline-dot" style={{ background: STATUS_COLORS[step.to] }} />
            <div className="timeline-body">
              <div className="timeline-title">{STEP_LABELS[step.to]}</div>
              <div className="muted timeline-time">{dateTime.format(new Date(step.at))}</div>
              {step.to === 'FILED' && issue.petition && step === history.steps.findLast((s) => s.to === 'FILED') && (
                <p>Radicado {issue.petition.radicado} ante {issue.agency}.</p>
              )}
              {reply && (
                <div className="timeline-detail">
                  <span className="badge">{CLASSIFICATION_LABELS[reply.classification]}</span>
                  <p>{reply.summary}</p>
                  <details>
                    <summary>Ver la respuesta completa</summary>
                    <p className="quote">{reply.body}</p>
                  </details>
                </div>
              )}
              {photo && (
                <div className="timeline-detail timeline-photo">
                  <img src={photo.photoUrl} alt={`Foto de verificación del ${dateTime.format(new Date(photo.at))}`} />
                  <p>{photo.reason}</p>
                </div>
              )}
            </div>
          </li>
        )
      })}
    </ol>
  )
}

function VerifyPanel({ issue, onVerified }: { issue: IssueSummary; onVerified: () => void }) {
  const { state: auth } = useAuth()
  const inputId = useId()
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<VerificationReceipt | null>(null)
  const [error, setError] = useState<string | null>(null)
  const next = `/dano?id=${issue.id}`

  async function send(file: File | undefined) {
    if (!file) return
    setBusy(true)
    setError(null)
    try {
      setResult(await verifyRepair(issue.id, file))
      onVerified()
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card verify-card" aria-live="polite">
      <h2>
        {issue.status === 'NEEDS_MORE_EVIDENCE' ? 'Necesitamos otra foto' : `${issue.agency ?? 'La entidad'} dice que lo reparó`}
      </h2>
      <p>
        ¿Pasas por ahí? Toma una foto actual del lugar, desde un ángulo parecido al de la foto original. La comparamos con
        la original y, si coinciden, el daño queda como reparado verificado.
      </p>
      {result ? (
        <Notice tone={OUTCOME_MESSAGES[result.outcome].tone}>
          {OUTCOME_MESSAGES[result.outcome].text} <span className="muted">{result.reason}</span>
        </Notice>
      ) : auth.status === 'anonymous' ? (
        <a className="button button-primary" href={href('/entrar', { next })}>
          Entra para enviar la foto
        </a>
      ) : auth.status === 'signedIn' && !auth.user.emailVerified ? (
        <a className="button button-secondary" href={href('/verificar', { next })}>
          Confirma tu correo para enviar la foto
        </a>
      ) : busy ? (
        <div className="verify-busy">
          <div className="spinner" aria-hidden="true" />
          Comparando con la foto original…
        </div>
      ) : (
        <>
          <input
            id={inputId}
            className="visually-hidden"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            capture="environment"
            onChange={(e) => {
              void send(e.target.files?.[0])
              e.target.value = ''
            }}
          />
          <label htmlFor={inputId} className="button button-primary">
            <Icon name="camera" size={18} />
            Enviar foto de verificación
          </label>
        </>
      )}
      {error && <Notice tone="error">{error}</Notice>}
    </section>
  )
}

function EndorseButton({ issue }: { issue: IssueSummary }) {
  const { state: auth } = useAuth()
  const [done, setDone] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const next = `/dano?id=${issue.id}`

  if (auth.status !== 'signedIn') {
    return (
      <a className="button button-secondary" href={href('/entrar', { next })}>
        <Icon name="eye" size={18} />
        Yo también lo vi
      </a>
    )
  }
  if (done) {
    return (
      <button type="button" className="button button-secondary is-done" disabled>
        <Icon name="checkCircle" size={18} />
        {done}
      </button>
    )
  }
  return (
    <button
      type="button"
      className="button button-secondary"
      disabled={busy}
      onClick={() => {
        setBusy(true)
        endorseIssue(issue.id)
          .then((r) => setDone(r.counted ? 'Contamos tu reporte' : 'Ya te habíamos contado'))
          .catch((e: unknown) => setDone(messageOf(e)))
          .finally(() => setBusy(false))
      }}
    >
      <Icon name="eye" size={18} />
      Yo también lo vi
    </button>
  )
}
