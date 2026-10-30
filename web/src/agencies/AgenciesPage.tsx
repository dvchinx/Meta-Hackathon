import { useEffect, useState } from 'react'
import { fetchScorecards, type AgencyScorecard } from '../api/agencies'
import { messageOf } from '../api/http'
import { Icon } from '../ui/Icon'
import { Notice } from '../ui/Notice'

type State = { kind: 'loading' } | { kind: 'ready'; cards: AgencyScorecard[] } | { kind: 'error'; message: string }

function plural(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`
}

function sum(cards: AgencyScorecard[], key: keyof AgencyScorecard): number {
  return cards.reduce((total, card) => total + (card[key] as number), 0)
}

/** Public accountability: how each agency answers petitions and whether its repairs hold up to a photo. */
export function AgenciesPage() {
  const [state, setState] = useState<State>({ kind: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    fetchScorecards(controller.signal)
      .then((cards) => setState({ kind: 'ready', cards }))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setState({ kind: 'error', message: messageOf(e) })
      })
    return () => controller.abort()
  }, [])

  return (
    <div className="page page-wide">
      <header className="page-header">
        <h1>Entidades</h1>
        <p>
          Cómo responde cada entidad por los daños que le corresponden. Una reparación solo cuenta cuando la foto
          nueva de un ciudadano la confirma: que la entidad diga que la hizo no basta.
        </p>
      </header>

      {state.kind === 'loading' && <div className="spinner" aria-label="Cargando" />}
      {state.kind === 'error' && <Notice tone="error">{state.message}</Notice>}
      {state.kind === 'ready' && state.cards.length === 0 && (
        <div className="card status-screen">
          <span className="status-icon info">
            <Icon name="list" size={28} />
          </span>
          <h2>Todavía no hay daños asignados a una entidad</h2>
          <p>Cuando un reporte se confirme, aquí verás cómo responde la entidad responsable.</p>
        </div>
      )}
      {state.kind === 'ready' && state.cards.length > 0 && <Scorecards cards={state.cards} />}
    </div>
  )
}

function Scorecards({ cards }: { cards: AgencyScorecard[] }) {
  const examples = sum(cards, 'examples')
  const claims = sum(cards, 'repairClaims')
  return (
    <>
      <section className="agency-totals" aria-label="Resumen">
        <Total value={sum(cards, 'overduePetitions')} label="peticiones vencidas sin respuesta" alert />
        <Total value={sum(cards, 'evasiveResponses')} label="respuestas evasivas" />
        <Total value={claims} label="reparaciones declaradas por las entidades" />
        <Total value={sum(cards, 'claimsConfirmed')} label="confirmadas con foto" good />
        <Total value={sum(cards, 'claimsRefuted')} label="desmentidas con foto" alert />
      </section>
      {examples > 0 && (
        <p className="agency-examples muted">
          Incluye {plural(examples, 'daño de ejemplo', 'daños de ejemplo')} de la demostración, marcados así en el
          mapa.
        </p>
      )}
      <ul className="agency-list">
        {cards.map((card) => (
          <li key={card.agencyCode}>
            <AgencyCard card={card} />
          </li>
        ))}
      </ul>
    </>
  )
}

function Total({ value, label, alert, good }: { value: number; label: string; alert?: boolean; good?: boolean }) {
  const tone = value > 0 && alert ? ' is-alert' : value > 0 && good ? ' is-good' : ''
  return (
    <div className="card agency-total">
      <span className={`agency-total-value${tone}`}>{value}</span>
      <span className="agency-total-label">{label}</span>
    </div>
  )
}

function AgencyCard({ card }: { card: AgencyScorecard }) {
  // A damage reopened and later verified counts in both groups.
  const pending = Math.max(0, card.repairClaims - card.claimsConfirmed - card.claimsRefuted)
  const responses = card.resolvingResponses + card.evasiveResponses
  return (
    <article className="card agency-card" aria-labelledby={`agency-${card.agencyCode}`}>
      <header className="agency-card-header">
        <h2 id={`agency-${card.agencyCode}`}>{card.agencyName}</h2>
        <span className="muted">
          {plural(card.issues, 'daño', 'daños')} · {plural(card.open, 'abierto', 'abiertos')}
          {card.verified > 0 && ` · ${plural(card.verified, 'verificado', 'verificados')}`}
        </span>
      </header>
      <dl className="agency-stats">
        <div>
          <dt>Abiertos, en promedio</dt>
          <dd>{card.averageDaysOpen === null ? '—' : plural(Math.round(card.averageDaysOpen), 'día', 'días')}</dd>
        </div>
        <div>
          <dt>Peticiones vencidas sin respuesta</dt>
          <dd className={card.overduePetitions > 0 ? 'is-alert' : undefined}>
            {card.overduePetitions}
            <span className="muted"> de {card.filed} radicadas</span>
          </dd>
        </div>
        <div>
          <dt>Respuestas evasivas</dt>
          <dd className={card.evasiveResponses > 0 ? 'is-alert' : undefined}>
            {card.evasiveResponses}
            <span className="muted"> de {responses}</span>
          </dd>
        </div>
        <div>
          <dt>Dijo que reparó</dt>
          <dd>{plural(card.repairClaims, 'daño', 'daños')}</dd>
        </div>
      </dl>
      {card.repairClaims > 0 && (
        <div className="claim-check">
          <div
            className="claim-bar"
            role="img"
            aria-label={`De ${card.repairClaims} reparaciones declaradas: ${card.claimsConfirmed} confirmadas con foto, ${card.claimsRefuted} desmentidas con foto y ${pending} sin confirmar.`}
          >
            <span className="claim-confirmed" style={{ flexGrow: card.claimsConfirmed }} />
            <span className="claim-refuted" style={{ flexGrow: card.claimsRefuted }} />
            <span className="claim-pending" style={{ flexGrow: pending }} />
          </div>
          <ul className="claim-legend">
            <li>
              <span className="dot claim-confirmed" />
              {card.claimsConfirmed} confirmadas con foto
            </li>
            <li>
              <span className="dot claim-refuted" />
              {card.claimsRefuted} desmentidas con foto
            </li>
            <li>
              <span className="dot claim-pending" />
              {pending} sin confirmar
            </li>
          </ul>
        </div>
      )}
      {card.examples > 0 && (
        <p className="muted agency-card-note">{plural(card.examples, 'daño de ejemplo', 'daños de ejemplo')}</p>
      )}
    </article>
  )
}
