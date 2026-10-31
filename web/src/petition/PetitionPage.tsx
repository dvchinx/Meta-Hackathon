import { useEffect, useState } from 'react'
import { messageOf } from '../api/http'
import { fetchPetition, type PetitionDocument } from '../api/petitions'
import { DAMAGE_LABELS } from '../issues/labels'
import { href } from '../routing'
import { Icon } from '../ui/Icon'
import { Notice } from '../ui/Notice'
import { ID_TYPE_LABELS } from './labels'

const longDate = new Intl.DateTimeFormat('es-CO', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'America/Bogota',
})
/** Calendar dates (YYYY-MM-DD) are not instants: format them without shifting the day. */
const calendarDate = (day: string) =>
  new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
    new Date(`${day}T00:00:00Z`),
  )

type State = { kind: 'loading' } | { kind: 'error'; message: string } | { kind: 'ready'; doc: PetitionDocument }

/** The derecho de petición as the agency would receive it. Printable ("Guardar como PDF"). */
export function PetitionPage({ petitionId }: { petitionId: string | null }) {
  const [state, setState] = useState<State>({ kind: 'loading' })

  useEffect(() => {
    if (!petitionId) return
    const controller = new AbortController()
    fetchPetition(petitionId, controller.signal)
      .then((doc) => setState({ kind: 'ready', doc }))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setState({ kind: 'error', message: messageOf(e) })
      })
    return () => controller.abort()
  }, [petitionId])

  if (!petitionId || state.kind === 'error') {
    return (
      <div className="page">
        <Notice tone="error">{state.kind === 'error' ? state.message : 'No encontramos esa petición.'}</Notice>
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

  const d = state.doc
  const p = d.petitioner
  const damage = DAMAGE_LABELS[d.damageType].toLowerCase()
  const where = d.locality ? `, localidad de ${d.locality}` : ''
  const mapUrl = `${window.location.origin}/${href('/mapa')}`

  return (
    <div className="page">
      <header className="page-header no-print">
        <a className="back-link" href={href('/mis-reportes')}>
          <Icon name="arrowLeft" size={16} />
          Mis reportes
        </a>
        <div className="form-actions">
          <button type="button" className="button button-primary" onClick={() => window.print()}>
            Imprimir o guardar PDF
          </button>
        </div>
        <Notice tone="warning">
          Radicación simulada: este documento no se envió a la entidad. Contiene tus datos personales: compártelo solo
          con quien deba recibirlo.
        </Notice>
      </header>

      <article className="card petition-document">
        <p>Bogotá D.C., {longDate.format(new Date(d.filedAt))}</p>
        <p className="petition-radicado">Radicado (simulado): {d.radicado}</p>

        <p>
          Señores
          <br />
          <strong>{d.agency}</strong>
          <br />
          Bogotá D.C.
        </p>

        <p>
          <strong>Asunto:</strong> Derecho de petición: {damage} en el espacio público{where}.
        </p>

        <p>
          {p.fullName}, identificado(a) con {ID_TYPE_LABELS[p.idType].toLowerCase()} número {p.idNumber}, en ejercicio
          del derecho fundamental de petición consagrado en el artículo 23 de la Constitución Política y regulado por
          la Ley 1755 de 2015, presento respetuosamente la siguiente petición.
        </p>

        <h2>Hechos</h2>
        <ol>
          <li>{d.subject}</li>
          <li>
            Ubicación: latitud {d.latitude.toFixed(6)}, longitud {d.longitude.toFixed(6)}
            {where}.
          </li>
          <li>
            El daño fue reportado por primera vez el {longDate.format(new Date(d.openedAt))} y suma{' '}
            {d.reportCount === 1 ? 'un reporte ciudadano' : `${d.reportCount} reportes ciudadanos`} en la plataforma
            Daños urbanos ({mapUrl}).
          </li>
        </ol>

        <h2>Peticiones</h2>
        <ol>
          <li>Realizar la visita técnica y la reparación del daño descrito.</li>
          <li>Informarme por escrito la fecha programada para la intervención.</li>
          <li>
            Si esta entidad no es la competente, dar traslado a la entidad competente en los términos del artículo 21
            de la Ley 1755 de 2015 e informarme de ello.
          </li>
        </ol>

        <h2>Anexos</h2>
        <p>Fotografía del daño, con caras y placas difuminadas.</p>
        {d.photoUrl && <img className="petition-photo" src={d.photoUrl} alt={`Foto del daño: ${damage}`} />}

        <h2>Notificaciones</h2>
        <p>
          Correo electrónico: {p.email}
          <br />
          Celular: {p.mobilePhone}
          {p.address && (
            <>
              <br />
              Dirección: {p.address}
            </>
          )}
        </p>

        <p className="petition-signature">
          Atentamente,
          <br />
          <br />
          {p.fullName}
          <br />
          {p.idType} {p.idNumber}
        </p>

        <p className="petition-footer">
          Plazo de respuesta: 15 días hábiles, hasta el {calendarDate(d.dueDate)}. Documento generado por Daños urbanos.
          Radicación simulada: no fue enviado a la entidad.
        </p>
      </article>
    </div>
  )
}
