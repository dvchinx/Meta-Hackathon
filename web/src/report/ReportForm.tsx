import { useEffect, useId, useRef, useState } from 'react'
import {
  ReportApiError,
  confirmReport,
  submitReport,
  type ReportAnalysis,
  type ReportConfirmation,
  type Urgency,
} from '../api/reports'
import { DAMAGE_LABELS } from '../issues/labels'
import { href } from '../routing'
import { Icon } from '../ui/Icon'
import { Notice } from '../ui/Notice'
import { LocationPicker, type PickedLocation } from './LocationPicker'

const URGENCY_LABELS: Record<Urgency, string> = { LOW: 'Baja', MEDIUM: 'Media', HIGH: 'Alta' }
const MAX_PHOTO_BYTES = 20 * 1024 * 1024
const MAX_TEXT = 1000

type Phase =
  | { kind: 'editing'; notice?: string }
  | { kind: 'analyzing' }
  | { kind: 'review'; analysis: ReportAnalysis }
  | { kind: 'confirming'; analysis: ReportAnalysis }
  | { kind: 'done'; confirmation: ReportConfirmation }
  | { kind: 'closed'; title: string; message: string }

const PHOTO_REJECTED =
  'No identificamos un daño en la vía pública en esa foto. Prueba con otra, más cerca del daño y con buena luz. Conservamos la ubicación.'

const CLOSED: Record<string, { title: string; message: string }> = {
  MANUAL_REVIEW: {
    title: 'Lo revisará una persona',
    message: 'Este tipo de daño está fuera de las categorías que analizamos automáticamente. Nuestro equipo lo revisará.',
  },
  MODELS_UNAVAILABLE: {
    title: 'Recibimos tu reporte',
    message: 'No pudimos analizar la foto en este momento. Nuestro equipo revisará tu reporte.',
  },
}

export function ReportForm({ onShowMap }: { onShowMap: () => void }) {
  const [photo, setPhoto] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [location, setLocation] = useState<PickedLocation | null>(null)
  const [text, setText] = useState('')
  const [phase, setPhase] = useState<Phase>({ kind: 'editing' })
  const [error, setError] = useState<string | null>(null)
  const photoInputId = useId()
  const previewUrl = useRef<string | null>(null)

  // Object URLs hold the file in memory until revoked.
  function showPreview(file: File | null) {
    if (previewUrl.current) URL.revokeObjectURL(previewUrl.current)
    previewUrl.current = file ? URL.createObjectURL(file) : null
    setPreview(previewUrl.current)
  }

  useEffect(
    () => () => {
      if (previewUrl.current) URL.revokeObjectURL(previewUrl.current)
    },
    [],
  )

  function choosePhoto(file: File | undefined) {
    setError(null)
    if (!file) return
    if (!file.type.startsWith('image/')) {
      setError('Elige una foto (JPG, PNG o WebP).')
      return
    }
    if (file.size > MAX_PHOTO_BYTES) {
      setError('La foto pesa más de 20 MB. Elige una más liviana.')
      return
    }
    setPhoto(file)
    showPreview(file)
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!photo || !location) return
    setError(null)
    setPhase({ kind: 'analyzing' })
    try {
      const analysis = await submitReport({ photo, ...location, text })
      if (analysis.outcome === 'AWAITING_CONFIRMATION') {
        setPhase({ kind: 'review', analysis })
      } else if (analysis.outcome === 'PHOTO_REJECTED') {
        setPhoto(null)
        showPreview(null)
        setPhase({ kind: 'editing', notice: PHOTO_REJECTED })
      } else {
        setPhase({ kind: 'closed', ...CLOSED[analysis.outcome] })
      }
    } catch (e) {
      setError(e instanceof ReportApiError ? e.message : 'No pudimos enviar el reporte. Intenta de nuevo.')
      setPhase({ kind: 'editing' })
    }
  }

  async function answer(analysis: ReportAnalysis, accepted: boolean) {
    setError(null)
    setPhase({ kind: 'confirming', analysis })
    try {
      const confirmation = await confirmReport(analysis.reportId, accepted)
      if (accepted) {
        setPhase({ kind: 'done', confirmation })
      } else {
        reset('Descartamos ese reporte. Puedes enviar otra foto cuando quieras.')
      }
    } catch (e) {
      setError(e instanceof ReportApiError ? e.message : 'No pudimos guardar tu respuesta. Intenta de nuevo.')
      setPhase({ kind: 'review', analysis })
    }
  }

  function reset(notice?: string) {
    setPhoto(null)
    showPreview(null)
    setText('')
    setPhase({ kind: 'editing', notice })
  }

  if (phase.kind === 'analyzing') {
    return (
      <div className="page">
        <div className="card status-screen" aria-live="polite">
          <div className="spinner" aria-hidden="true" />
          <h1>Analizando la foto</h1>
          <p>Esto toma unos segundos.</p>
          <ul className="progress-steps">
            <li>
              <Icon name="layers" size={16} />
              Identificamos el tipo de daño y su urgencia
            </li>
            <li>
              <Icon name="camera" size={16} />
              Marcamos el área afectada
            </li>
            <li>
              <Icon name="shield" size={16} />
              Difuminamos caras y placas
            </li>
          </ul>
        </div>
      </div>
    )
  }

  if (phase.kind === 'review' || phase.kind === 'confirming') {
    const a = phase.analysis
    const busy = phase.kind === 'confirming'
    return (
      <div className="page">
        <header className="page-header">
          <h1>Revisa tu reporte</h1>
          <p>Confirma que la descripción corresponde al daño. Después lo publicamos en el mapa.</p>
        </header>
        <article className="card review-card">
          {a.markedPhotoUrl && <img className="review-photo" src={a.markedPhotoUrl} alt="Foto con el daño marcado" />}
          <div className="review-body">
            {a.callEmergencyLine && (
              <Notice tone="error">
                <strong>Si hay peligro inmediato, llama ya a la Línea 123.</strong> Este reporte no reemplaza esa
                llamada.
              </Notice>
            )}
            <p className="review-summary">{a.summary}</p>
            <dl className="facts">
              <div>
                <dt>Tipo de daño</dt>
                <dd>{a.damageType ? DAMAGE_LABELS[a.damageType] : 'Sin clasificar'}</dd>
              </div>
              {a.urgency && (
                <div>
                  <dt>Urgencia</dt>
                  <dd>
                    <span className={`badge badge-${a.urgency.toLowerCase()}`}>{URGENCY_LABELS[a.urgency]}</span>
                  </dd>
                </div>
              )}
              {a.agency && (
                <div className="wide">
                  <dt>Entidad responsable</dt>
                  <dd>{a.agency}</dd>
                </div>
              )}
              {a.locality && (
                <div>
                  <dt>Localidad</dt>
                  <dd>{a.locality}</dd>
                </div>
              )}
              {a.locationHints.length > 0 && (
                <div className="wide">
                  <dt>Referencias</dt>
                  <dd>{a.locationHints.join(' · ')}</dd>
                </div>
              )}
            </dl>
            <p className="privacy">
              <Icon name="shield" size={16} />
              Las caras y placas aparecen difuminadas. Solo publicamos esta versión de la foto.
            </p>
            {error && <Notice tone="error">{error}</Notice>}
          </div>
          <div className="review-actions">
            <button type="button" className="button button-primary" onClick={() => answer(a, true)} disabled={busy}>
              Sí, es correcto
            </button>
            <button type="button" className="button button-secondary" onClick={() => answer(a, false)} disabled={busy}>
              No, descartar
            </button>
          </div>
        </article>
      </div>
    )
  }

  if (phase.kind === 'done') {
    const c = phase.confirmation
    return (
      <div className="page">
        <div className="card status-screen">
          <span className="status-icon success">
            <Icon name="checkCircle" size={28} />
          </span>
          <h1>Reporte registrado</h1>
          <p>
            {c.newIssue
              ? 'Es el primer reporte de este daño. Ya aparece en el mapa.'
              : `Otras personas ya habían reportado este daño: ahora son ${c.reportCount} reportes.`}
          </p>
          <p>Lo daremos por reparado solo cuando una foto nueva lo confirme.</p>
          <p>
            ¿Quieres exigir la reparación? Radica un derecho de petición ante la entidad: tiene 15 días hábiles para
            responder.
          </p>
          <div className="status-actions">
            {c.issueId && (
              <a className="button button-primary" href={href('/radicar', { issue: c.issueId })}>
                <Icon name="mail" size={18} />
                Radicar petición
              </a>
            )}
            <button type="button" className="button button-secondary" onClick={onShowMap}>
              <Icon name="map" size={18} />
              Ver en el mapa
            </button>
            <button type="button" className="button button-secondary" onClick={() => reset()}>
              Reportar otro daño
            </button>
          </div>
        </div>
      </div>
    )
  }

  if (phase.kind === 'closed') {
    return (
      <div className="page">
        <div className="card status-screen">
          <span className="status-icon info">
            <Icon name="info" size={28} />
          </span>
          <h1>{phase.title}</h1>
          <p>{phase.message}</p>
          <div className="status-actions">
            <button type="button" className="button button-secondary" onClick={() => reset()}>
              Reportar otro daño
            </button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="page">
      <header className="page-header">
        <a className="back-link" href={href('/mapa')}>
          <Icon name="arrowLeft" size={16} />
          Volver al mapa
        </a>
        <h1>Reportar un daño</h1>
        <p>
          Huecos, alcantarillas, postes, árboles en riesgo, alumbrado, fugas de agua, sumideros, basuras, andenes y
          mobiliario urbano en Bogotá.
        </p>
      </header>

      <form className="card form-card" onSubmit={submit} noValidate>
        {phase.notice && (
          <div className="step">
            <Notice tone="warning">{phase.notice}</Notice>
          </div>
        )}

        <section className="step" aria-labelledby={`${photoInputId}-title`}>
          <div className="step-head">
            <span className={photo ? 'step-number done' : 'step-number'}>1</span>
            <h2 id={`${photoInputId}-title`}>Foto del daño</h2>
          </div>
          <input
            id={photoInputId}
            className="visually-hidden"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            capture="environment"
            onChange={(e) => {
              choosePhoto(e.target.files?.[0])
              e.target.value = ''
            }}
          />
          {preview ? (
            <div className="photo-frame">
              <img src={preview} alt="Foto seleccionada" />
              <label htmlFor={photoInputId} className="button button-secondary photo-change">
                <Icon name="refresh" size={16} />
                Cambiar foto
              </label>
            </div>
          ) : (
            <label htmlFor={photoInputId} className="dropzone">
              <Icon name="camera" size={28} />
              <span className="dropzone-title">Tomar o elegir una foto</span>
              <span className="dropzone-hint">JPG, PNG o WebP · hasta 20 MB</span>
            </label>
          )}
        </section>

        <section className="step">
          <div className="step-head">
            <span className={location ? 'step-number done' : 'step-number'}>2</span>
            <h2>Ubicación</h2>
          </div>
          <LocationPicker value={location} onChange={setLocation} />
        </section>

        <section className="step">
          <div className="step-head">
            <span className={text.trim() ? 'step-number done' : 'step-number'}>3</span>
            <h2>
              Detalles<span className="optional">(opcional)</span>
            </h2>
          </div>
          <textarea
            className="textarea"
            aria-label="Detalles del daño"
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={MAX_TEXT}
            placeholder="Por ejemplo: hueco profundo en el carril derecho, ya se han caído motos."
          />
          <div className="field-meta">
            {text.length}/{MAX_TEXT}
          </div>
        </section>

        <div className="form-footer">
          {error && <Notice tone="error">{error}</Notice>}
          <button type="submit" className="button button-primary button-lg button-block" disabled={!photo || !location}>
            Enviar reporte
          </button>
          <p className="privacy">
            <Icon name="shield" size={16} />
            Tu nombre no se publica. Difuminamos caras y placas antes de publicar cualquier foto.
          </p>
        </div>
      </form>
    </div>
  )
}
