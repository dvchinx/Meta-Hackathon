import { useEffect, useState } from 'react'
import { messageOf } from '../api/http'
import { fetchMyReports, type MyReport, type ReportStatus } from '../api/reports'
import { DAMAGE_LABELS, STATUS_COLORS, STATUS_LABELS, canFile, deadlineLabel, reportsLabel } from '../issues/labels'
import { href } from '../routing'
import { Icon } from '../ui/Icon'
import { Notice } from '../ui/Notice'

const REPORT_STATUS_LABELS: Record<ReportStatus, string> = {
  COLLECTING: 'Incompleto',
  ANALYZING: 'En análisis',
  AWAITING_CONFIRMATION: 'Pendiente de tu confirmación',
  CONFIRMED: 'Confirmado',
  CANCELLED: 'Descartado',
  MANUAL_REVIEW: 'En revisión manual',
}

const dateFormat = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', year: 'numeric' })

type State = { kind: 'loading' } | { kind: 'ready'; reports: MyReport[] } | { kind: 'error'; message: string }

export function MyReportsPage() {
  const [state, setState] = useState<State>({ kind: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    fetchMyReports(controller.signal)
      .then((reports) => setState({ kind: 'ready', reports }))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setState({ kind: 'error', message: messageOf(e) })
      })
    return () => controller.abort()
  }, [])

  return (
    <div className="page">
      <header className="page-header">
        <h1>Mis reportes</h1>
        <p>Lo que has reportado y en qué va cada daño.</p>
      </header>

      {state.kind === 'loading' && <div className="spinner" aria-label="Cargando" />}
      {state.kind === 'error' && <Notice tone="error">{state.message}</Notice>}
      {state.kind === 'ready' && state.reports.length === 0 && (
        <div className="card status-screen">
          <span className="status-icon info">
            <Icon name="list" size={28} />
          </span>
          <h2>Todavía no has reportado nada</h2>
          <p>Cuando reportes un daño, aquí verás su estado: radicado, respondido, verificado.</p>
          <div className="status-actions">
            <a className="button button-primary" href={href('/reportar')}>
              <Icon name="plus" size={18} />
              Reportar un daño
            </a>
          </div>
        </div>
      )}
      {state.kind === 'ready' && state.reports.length > 0 && (
        <ul className="report-list">
          {state.reports.map((r) => (
            <li key={r.reportId} className="card report-item">
              {r.photoUrl ? (
                <img className="report-thumb" src={r.photoUrl} alt="" />
              ) : (
                <div className="report-thumb report-thumb-empty">
                  <Icon name="camera" size={20} />
                </div>
              )}
              <div className="report-item-body">
                <div className="report-item-title">
                  {r.damageType ? DAMAGE_LABELS[r.damageType] : 'Daño'}
                  <span className="muted"> · {dateFormat.format(new Date(r.createdAt))}</span>
                </div>
                {r.summary && <p className="report-item-summary">{r.summary}</p>}
                {r.issueStatus ? (
                  <div className="issue-popup-status">
                    <span className="dot" style={{ background: STATUS_COLORS[r.issueStatus] }} />
                    {STATUS_LABELS[r.issueStatus]}
                    <span className="muted"> · {reportsLabel(r.issueReportCount)}</span>
                  </div>
                ) : (
                  <div className="muted">{REPORT_STATUS_LABELS[r.reportStatus]}</div>
                )}
                {r.agency && (
                  <div className="muted report-item-meta">
                    {r.agency}
                    {r.locality && ` · ${r.locality}`}
                  </div>
                )}
                {r.petition && (
                  <div className="muted report-item-meta">
                    Radicado {r.petition.radicado}
                    {r.issueStatus === 'FILED' && ` · ${deadlineLabel(r.petition)}`}
                  </div>
                )}
                {r.issueId && r.issueStatus && (
                  <div className="report-item-actions">
                    {canFile({ status: r.issueStatus, agency: r.agency }) && (
                      <a className="button button-primary button-sm" href={href('/radicar', { issue: r.issueId })}>
                        Radicar petición
                      </a>
                    )}
                    <a className="button button-ghost button-sm" href={href('/dano', { id: r.issueId })}>
                      Ver detalle
                    </a>
                    {r.myPetitionId && (
                      <a className="button button-secondary button-sm" href={href('/peticion', { id: r.myPetitionId })}>
                        Ver mi petición
                      </a>
                    )}
                  </div>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
