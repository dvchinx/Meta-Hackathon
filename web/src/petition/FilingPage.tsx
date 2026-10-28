import { useEffect, useId, useState } from 'react'
import { messageOf } from '../api/http'
import { fetchIssue } from '../api/issues'
import { fetchFilerProfile, filePetition, type FilerProfile, type PetitionReceipt } from '../api/petitions'
import { DAMAGE_LABELS, STATUS_LABELS, canFile, deadlineLabel } from '../issues/labels'
import type { IssueSummary } from '../issues/types'
import { href } from '../routing'
import { Icon } from '../ui/Icon'
import { Notice } from '../ui/Notice'
import { FilerProfileForm, FilerProfileSummary } from './FilerProfileForm'
import { OfficialChannelLink } from './OfficialChannelLink'

type State =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; issue: IssueSummary; profile: FilerProfile | null; editing: boolean }
  | { kind: 'filed'; issue: IssueSummary; receipt: PetitionReceipt }

const dateFormat = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })

/** Simulated filing of a derecho de petición, with the citizen's own data and explicit consent to share it. */
export function FilingPage({ issueId }: { issueId: string | null }) {
  const [state, setState] = useState<State>({ kind: 'loading' })
  const [consent, setConsent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const consentId = useId()

  useEffect(() => {
    if (!issueId) return
    const controller = new AbortController()
    Promise.all([fetchIssue(issueId, controller.signal), fetchFilerProfile(controller.signal)])
      .then(([issue, profile]) => setState({ kind: 'ready', issue, profile, editing: profile === null }))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setState({ kind: 'error', message: messageOf(e) })
      })
    return () => controller.abort()
  }, [issueId])

  async function submit(issue: IssueSummary) {
    setError(null)
    setBusy(true)
    try {
      setState({ kind: 'filed', issue, receipt: await filePetition(issue.id) })
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

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
        <Notice tone="error">
          {state.kind === 'error' ? state.message : 'Falta el daño que quieres radicar. Elígelo en el mapa.'}
        </Notice>
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

  if (state.kind === 'filed') {
    const { receipt, issue } = state
    return (
      <div className="page">
        <div className="card status-screen">
          <span className="status-icon success">
            <Icon name="checkCircle" size={28} />
          </span>
          <h1>Petición radicada</h1>
          <p>
            Radicado <strong>{receipt.radicado}</strong> ante {receipt.agency}. Tiene plazo para responder hasta el{' '}
            {dateFormat.format(new Date(`${receipt.dueDate}T00:00:00Z`))} (15 días hábiles).
          </p>
          <Notice tone="warning">
            Es una <strong>simulación</strong>: no enviamos nada a la entidad. El seguimiento en el mapa sí es real.
          </Notice>
          {issue.officialChannel && <OfficialChannelLink channel={issue.officialChannel} />}
          <div className="status-actions">
            <a className="button button-primary" href={href('/peticion', { id: receipt.petitionId })}>
              Ver e imprimir la petición
            </a>
            <a className="button button-secondary" href={href('/')}>
              <Icon name="map" size={18} />
              Volver al mapa
            </a>
          </div>
        </div>
      </div>
    )
  }

  const { issue, profile, editing } = state
  const place = issue.locality ? ` en ${issue.locality}` : ''

  if (!canFile(issue)) {
    return (
      <div className="page">
        <header className="page-header">
          {back}
          <h1>Radicar petición</h1>
        </header>
        <div className="card status-screen">
          <span className="status-icon info">
            <Icon name="info" size={28} />
          </span>
          <h2>
            {DAMAGE_LABELS[issue.damageType]}
            {place}
          </h2>
          {issue.petition ? (
            <p>
              Este daño ya tiene una petición: radicado <strong>{issue.petition.radicado}</strong>.{' '}
              {issue.status === 'FILED' && deadlineLabel(issue.petition)}
            </p>
          ) : issue.agency === null ? (
            <p>Todavía no sabemos qué entidad es responsable de este daño. Nuestro equipo lo revisará.</p>
          ) : (
            <p>Estado actual: {STATUS_LABELS[issue.status]}. Ahora no se puede radicar otra petición.</p>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="page">
      <header className="page-header">
        {back}
        <h1>Radicar petición</h1>
        <p>
          Un derecho de petición obliga a la entidad a responder en 15 días hábiles (Ley 1755 de 2015). Lo armamos con
          tus datos y la evidencia del mapa.
        </p>
      </header>

      <div className="card form-card">
        <section className="step">
          <div className="step-head">
            <span className="step-number done">1</span>
            <h2>El daño</h2>
          </div>
          <div className="filing-issue">
            {issue.photoUrl && <img src={issue.photoUrl} alt={`Foto del daño: ${DAMAGE_LABELS[issue.damageType]}`} />}
            <dl className="facts">
              <div className="wide">
                <dt>Daño</dt>
                <dd>
                  {DAMAGE_LABELS[issue.damageType]}
                  {place}
                </dd>
              </div>
              <div className="wide">
                <dt>Entidad responsable</dt>
                <dd>{issue.agency}</dd>
              </div>
              <div>
                <dt>Reportes</dt>
                <dd>{issue.reportCount}</dd>
              </div>
            </dl>
          </div>
        </section>

        <section className="step">
          <div className="step-head">
            <span className={profile && !editing ? 'step-number done' : 'step-number'}>2</span>
            <h2>Tus datos</h2>
          </div>
          {editing || !profile ? (
            <FilerProfileForm
              initial={profile}
              submitLabel="Guardar y continuar"
              onSaved={(saved) => setState({ ...state, profile: saved, editing: false })}
              onCancel={profile ? () => setState({ ...state, editing: false }) : undefined}
            />
          ) : (
            <div className="stack">
              <FilerProfileSummary profile={profile} />
              <button
                type="button"
                className="button button-ghost button-inline"
                onClick={() => setState({ ...state, editing: true })}
              >
                Corregir mis datos
              </button>
            </div>
          )}
        </section>

        <section className="step">
          <div className="step-head">
            <span className="step-number">3</span>
            <h2>Autoriza y radica</h2>
          </div>
          <div className="stack">
            <div className="checkbox">
              <input
                id={consentId}
                type="checkbox"
                checked={consent}
                disabled={!profile || editing}
                onChange={(e) => setConsent(e.target.checked)}
              />
              <label htmlFor={consentId}>
                Autorizo compartir mis datos de identificación y contacto con <strong>{issue.agency}</strong> para
                tramitar esta petición, según la{' '}
                <a href={href('/privacidad')} target="_blank" rel="noreferrer">
                  Política de tratamiento de datos
                </a>
                .
              </label>
            </div>
            <Notice tone="warning">
              Radicación <strong>simulada</strong>: generamos el radicado y el documento, pero no lo enviamos a la
              entidad.
            </Notice>
            {error && <Notice tone="error">{error}</Notice>}
            <button
              type="button"
              className="button button-primary button-lg button-block"
              disabled={!profile || editing || !consent || busy}
              onClick={() => submit(issue)}
            >
              {busy ? 'Radicando…' : 'Radicar petición'}
            </button>
            {issue.officialChannel && <OfficialChannelLink channel={issue.officialChannel} />}
          </div>
        </section>
      </div>
    </div>
  )
}
