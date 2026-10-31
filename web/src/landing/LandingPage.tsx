import { useEffect, useState, type CSSProperties, type ReactNode } from 'react'
import { fetchScorecards, type AgencyScorecard } from '../api/agencies'
import { fetchIssueHistory, fetchIssues } from '../api/issues'
import { DAMAGE_LABELS } from '../issues/labels'
import type { DamageType, IssueSummary } from '../issues/types'
import { href } from '../routing'
import { DamageIcon } from '../ui/DamageIcon'
import { Icon } from '../ui/Icon'
import './landing.css'
import { useCountUp, useInView } from './motion'

/** The first page for people who have not signed in: what the platform does, live numbers and a real before/after. */
export function LandingPage() {
  const [issues, setIssues] = useState<IssueSummary[] | null>(null)
  const [scorecards, setScorecards] = useState<AgencyScorecard[] | null>(null)
  const [proof, setProof] = useState<Proof | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    fetchIssues(controller.signal)
      .then(async (all) => {
        setIssues(all)
        setProof(await findProof(all, controller.signal))
      })
      .catch(() => {
        // The page reads fine without live numbers: those sections just stay out.
      })
    fetchScorecards(controller.signal)
      .then(setScorecards)
      .catch(() => {})
    return () => controller.abort()
  }, [])

  return (
    <div className="landing">
      <Hero />
      {issues && issues.length > 0 && <LiveNumbers issues={issues} />}
      <DamageMarquee />
      <HowItWorks />
      <PhotoRule proof={proof} />
      {scorecards && scorecards.length > 0 && <Accountability cards={scorecards} />}
      <FinalCall />
      <footer className="landing-footer">
        <p>
          Proyecto para el Meta Global AI Developer Hackathon. Hecho con Muse Spark y SAM 3.1. La radicación de
          peticiones es simulada en esta versión.
        </p>
        <nav aria-label="Enlaces">
          <a href={href('/mapa')}>Mapa</a>
          <a href={href('/entidades')}>Entidades</a>
          <a href={href('/privacidad')}>Tratamiento de datos</a>
        </nav>
      </footer>
    </div>
  )
}

interface Proof {
  issue: IssueSummary
  before: string
  after: string
  reason: string
}

/** A damage whose repair a photo confirmed, with both photos: the product's rule, shown with real data. */
async function findProof(issues: IssueSummary[], signal: AbortSignal): Promise<Proof | null> {
  const verified = issues.filter((i) => i.status === 'VERIFIED' && i.photoUrl)
  for (const issue of verified.slice(0, 3)) {
    try {
      const history = await fetchIssueHistory(issue.id, signal)
      const check = history.verifications.findLast((v) => v.outcome === 'REPAIRED')
      if (check && issue.photoUrl) {
        return { issue, before: issue.photoUrl, after: check.photoUrl, reason: check.reason }
      }
    } catch {
      return null
    }
  }
  return null
}

// ---------- Hero ----------

const PINS: { x: number; y: number; delay: number; tone: 'red' | 'orange' | 'green' }[] = [
  { x: 8, y: 22, delay: 0.2, tone: 'red' },
  { x: 18, y: 64, delay: 0.9, tone: 'orange' },
  { x: 33, y: 12, delay: 1.6, tone: 'green' },
  { x: 47, y: 82, delay: 2.3, tone: 'red' },
  { x: 88, y: 18, delay: 1.2, tone: 'orange' },
  { x: 94, y: 58, delay: 2.0, tone: 'red' },
  { x: 72, y: 88, delay: 2.7, tone: 'green' },
]

function Hero() {
  return (
    <section className="landing-hero">
      <div className="landing-hero-backdrop" aria-hidden="true">
        <span className="blob blob-a" />
        <span className="blob blob-b" />
        <span className="blob blob-c" />
        <svg className="street-grid" viewBox="0 0 100 100" preserveAspectRatio="none">
          <path d="M0 30 L100 22 M0 55 L100 61 M0 82 L100 76 M20 0 L26 100 M52 0 L46 100 M80 0 L84 100" />
        </svg>
        {PINS.map((pin) => (
          <span
            key={`${pin.x}-${pin.y}`}
            className={`hero-pin pin-${pin.tone}`}
            style={{ left: `${pin.x}%`, top: `${pin.y}%`, animationDelay: `${pin.delay}s` } as CSSProperties}
          >
            <span className="hero-pin-ring" style={{ animationDelay: `${pin.delay + 0.6}s` }} />
          </span>
        ))}
      </div>

      <div className="landing-hero-inner">
        <div className="landing-hero-copy">
          <p className="landing-eyebrow rise" style={{ '--d': '0s' } as CSSProperties}>
            <span className="eyebrow-dot" />
            Bogotá · Reportes ciudadanos
          </p>
          <h1 className="landing-title">
            <span className="rise" style={{ '--d': '0.1s' } as CSSProperties}>
              Que el daño de tu cuadra
            </span>{' '}
            <span className="rise" style={{ '--d': '0.25s' } as CSSProperties}>
              no se quede en <span className="title-mark">queja</span>.
            </span>
          </h1>
          <p className="landing-lead rise" style={{ '--d': '0.4s' } as CSSProperties}>
            Toma una foto de un hueco, una alcantarilla destapada o un poste caído. La IA identifica el daño, difumina
            caras y placas, y te dice qué entidad debe arreglarlo. Solo lo damos por arreglado cuando otra foto lo
            demuestra.
          </p>
          <div className="landing-actions rise" style={{ '--d': '0.55s' } as CSSProperties}>
            <a className="button button-primary button-lg landing-cta" href={href('/reportar')}>
              <Icon name="camera" size={20} />
              Reportar un daño
            </a>
            <a className="button button-secondary button-lg" href={href('/mapa')}>
              <Icon name="map" size={20} />
              Ver el mapa
            </a>
          </div>
          <p className="landing-emergency rise" style={{ '--d': '0.7s' } as CSSProperties}>
            <Icon name="alertTriangle" size={16} />
            Si hay riesgo inmediato, llama primero a la Línea 123.
          </p>
        </div>
        <ScanDemo />
      </div>
    </section>
  )
}

/** A street photo going through the pipeline, on a loop: scan, damage mask, blurring, then what the citizen reads. */
function ScanDemo() {
  return (
    <figure className="scan-demo" aria-label="Ejemplo: la IA marca un hueco en la foto y difumina una cara y una placa">
      <div className="scan-photo">
        <svg viewBox="0 0 400 300" role="presentation" aria-hidden="true">
          <defs>
            <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="#cfe6ff" />
              <stop offset="1" stopColor="#f3f8ff" />
            </linearGradient>
            <linearGradient id="road" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor="#9aa1ab" />
              <stop offset="1" stopColor="#6b727d" />
            </linearGradient>
            <radialGradient id="hole" cx="0.5" cy="0.45" r="0.6">
              <stop offset="0" stopColor="#3a3f47" />
              <stop offset="0.7" stopColor="#4b525c" />
              <stop offset="1" stopColor="#5d646e" />
            </radialGradient>
          </defs>
          <rect width="400" height="300" fill="url(#sky)" />
          <g className="scan-clouds">
            <ellipse cx="80" cy="40" rx="34" ry="10" fill="#fff" />
            <ellipse cx="300" cy="28" rx="44" ry="11" fill="#fff" />
          </g>
          <rect x="0" y="62" width="70" height="98" fill="#e8d5c4" />
          <rect x="70" y="40" width="58" height="120" fill="#d7dde5" />
          <rect x="272" y="50" width="64" height="110" fill="#f1d9a7" />
          <rect x="336" y="74" width="64" height="86" fill="#dcc7e6" />
          {[0, 1, 2, 3].map((row) =>
            [0, 1].map((col) => (
              <rect key={`${row}-${col}`} x={82 + col * 22} y={52 + row * 24} width="12" height="14" fill="#aab6c4" />
            )),
          )}
          {[0, 1, 2].map((row) => (
            <rect key={row} x="286" y={64 + row * 28} width="36" height="12" fill="#c9a86a" />
          ))}
          <circle cx="205" cy="110" r="30" fill="#5f9f6a" />
          <rect x="201" y="124" width="8" height="36" fill="#7b5a43" />
          <rect x="0" y="160" width="400" height="20" fill="#d0d5dd" />
          <path d="M0 180 H400 V300 H0 Z" fill="url(#road)" />
          <path d="M200 180 L196 210 M193 228 L188 262 M185 280 L182 300" stroke="#f5f5f5" strokeWidth="5" />
          <ellipse cx="248" cy="246" rx="64" ry="22" fill="url(#hole)" />
          <ellipse cx="240" cy="243" rx="40" ry="10" fill="#7e98ad" opacity="0.65" />
          <g className="scan-person">
            <circle cx="52" cy="128" r="9" fill="#8a5a44" />
            <rect x="43" y="138" width="18" height="30" rx="6" fill="#2f6fb3" />
          </g>
          <g>
            <rect x="318" y="188" width="76" height="34" rx="9" fill="#e45c4c" />
            <rect x="326" y="172" width="54" height="22" rx="7" fill="#ef7b6c" />
            <circle cx="334" cy="224" r="8" fill="#2b2f36" />
            <circle cx="380" cy="224" r="8" fill="#2b2f36" />
            <rect x="340" y="204" width="30" height="10" rx="2" fill="#fff4b8" />
          </g>
          <ellipse
            className="scan-mask"
            cx="248"
            cy="246"
            rx="68"
            ry="25"
            fill="rgb(217 45 32 / 28%)"
            stroke="#d92d20"
            strokeWidth="3"
            pathLength="100"
          />
          <rect className="scan-blur" x="38" y="114" width="28" height="28" rx="6" />
          <rect className="scan-blur" x="335" y="200" width="40" height="18" rx="4" />
        </svg>
        <span className="scan-beam" aria-hidden="true" />
      </div>
      <div className="scan-chip chip-type">
        <DamageIcon type="POTHOLE" size={16} decorative />
        Hueco · urgencia media
      </div>
      <div className="scan-chip chip-agency">
        <Icon name="shield" size={16} />
        Unidad de Mantenimiento Vial
      </div>
      <div className="scan-chip chip-privacy">
        <Icon name="eye" size={16} />
        Cara y placa difuminadas
      </div>
      <figcaption>Así se ve un reporte mientras lo analiza la IA (ilustración).</figcaption>
    </figure>
  )
}

// ---------- Live numbers ----------

function LiveNumbers({ issues }: { issues: IssueSummary[] }) {
  const [ref, visible] = useInView<HTMLElement>(0.3)
  const reports = issues.reduce((total, i) => total + i.reportCount, 0)
  const verified = issues.filter((i) => i.status === 'VERIFIED').length
  const localities = new Set(issues.map((i) => i.locality).filter(Boolean)).size
  const examples = issues.filter((i) => i.example).length
  return (
    <section ref={ref} className={`landing-numbers reveal${visible ? ' is-visible' : ''}`} aria-label="En cifras">
      <Counter value={issues.length} label="daños en el mapa" start={visible} />
      <Counter value={reports} label="reportes y apoyos de vecinos" start={visible} />
      <Counter value={localities} label="localidades" start={visible} />
      <Counter value={verified} label="reparaciones confirmadas con foto" start={visible} tone="good" />
      {examples > 0 && (
        <p className="landing-numbers-note">
          Incluye {examples} {examples === 1 ? 'daño de ejemplo' : 'daños de ejemplo'} de la demostración, marcados
          así en el mapa.
        </p>
      )}
    </section>
  )
}

function Counter({ value, label, start, tone }: { value: number; label: string; start: boolean; tone?: 'good' | 'alert' }) {
  const shown = useCountUp(value, start)
  return (
    <div className="landing-counter">
      <span className={`landing-counter-value${tone ? ` is-${tone}` : ''}`} aria-hidden="true">
        {shown}
      </span>
      <span className="landing-counter-label">
        <span className="visually-hidden">{value} </span>
        {label}
      </span>
    </div>
  )
}

// ---------- Damage types ----------

const TYPES = Object.keys(DAMAGE_LABELS).filter((t) => t !== 'OTHER') as DamageType[]

function DamageMarquee() {
  return (
    <section className="landing-marquee" aria-label="Qué puedes reportar">
      <h2 className="landing-marquee-title">Qué puedes reportar</h2>
      <div className="marquee">
        {/* Two copies of the list scroll as one: the second takes the first one's place without a jump. */}
        {[0, 1].map((copy) => (
          <ul key={copy} className="marquee-track" aria-hidden={copy === 1 ? true : undefined}>
            {TYPES.map((type) => (
              <li key={type} className="marquee-chip">
                <DamageIcon type={type} size={20} decorative />
                {DAMAGE_LABELS[type]}
              </li>
            ))}
          </ul>
        ))}
      </div>
    </section>
  )
}

// ---------- How it works ----------

const STEPS: { icon: 'camera' | 'eye' | 'mail' | 'checkCircle'; title: string; body: ReactNode }[] = [
  {
    icon: 'camera',
    title: 'Toma la foto',
    body: 'Desde el celular, con la ubicación marcada en el mapa. Toma menos de un minuto.',
  },
  {
    icon: 'eye',
    title: 'La IA la lee',
    body: 'Muse Spark reconoce el tipo de daño y su urgencia. SAM 3.1 lo marca en la foto y difumina caras y placas.',
  },
  {
    icon: 'mail',
    title: 'Radicas la petición',
    body: 'Con tus datos, guardados cifrados, sale el derecho de petición para la entidad responsable. La ley le da 15 días hábiles.',
  },
  {
    icon: 'checkCircle',
    title: 'Una foto lo confirma',
    body: 'Cuando la entidad dice que lo arregló, un vecino sube una foto nueva. Si el daño sigue ahí, el caso se reabre.',
  },
]

function HowItWorks() {
  const [ref, visible] = useInView<HTMLElement>(0.25)
  return (
    <section ref={ref} className={`landing-section landing-steps${visible ? ' is-visible' : ''}`}>
      <header className="landing-section-header">
        <p className="landing-kicker">Cómo funciona</p>
        <h2>De la foto al arreglo, sin perder el rastro</h2>
      </header>
      <div className="steps-line" aria-hidden="true">
        <span />
      </div>
      <ol className="steps">
        {STEPS.map((step, i) => (
          <li key={step.title} className="step-card" style={{ '--d': `${0.15 + i * 0.15}s` } as CSSProperties}>
            <span className="step-icon">
              <Icon name={step.icon} size={24} />
              <span className="step-number">{i + 1}</span>
            </span>
            <h3>{step.title}</h3>
            <p>{step.body}</p>
          </li>
        ))}
      </ol>
    </section>
  )
}

// ---------- The photo rule ----------

const TIMELINE = ['Reportado', 'Petición radicada', 'La entidad respondió', 'Dice que lo arregló', 'Verificado con foto']

function PhotoRule({ proof }: { proof: Proof | null }) {
  const [ref, visible] = useInView<HTMLElement>(0.25)
  return (
    <section ref={ref} className={`landing-section landing-rule${visible ? ' is-visible' : ''}`}>
      <div className="rule-copy">
        <p className="landing-kicker">La regla</p>
        <h2>La palabra de la entidad no cierra el caso</h2>
        <p>
          Un daño solo queda como reparado cuando una foto nueva lo demuestra. La IA compara las dos fotos del mismo
          punto y, si duda, pide otra: preferimos reabrir un caso de más que cerrar uno que sigue roto.
        </p>
        <ol className="state-sequence" aria-label="Estados de un daño">
          {TIMELINE.map((label, i) => (
            <li key={label} style={{ '--i': i } as CSSProperties} className={i === TIMELINE.length - 1 ? 'is-final' : undefined}>
              <span className="state-dot" />
              {label}
            </li>
          ))}
        </ol>
      </div>
      {proof ? <BeforeAfter proof={proof} start={visible} /> : <RulePlaceholder />}
    </section>
  )
}

function BeforeAfter({ proof, start }: { proof: Proof; start: boolean }) {
  const [position, setPosition] = useState(50)
  const [touched, setTouched] = useState(false)
  const { issue } = proof
  return (
    <figure className={`before-after-demo${start && !touched ? ' is-teasing' : ''}`}>
      <div className="compare" style={{ '--pos': `${position}%` } as CSSProperties}>
        <img src={proof.after} alt="Después: foto que confirmó la reparación" />
        <div className="compare-before">
          <img src={proof.before} alt="Antes: foto del reporte original" />
        </div>
        <span className="compare-handle" aria-hidden="true">
          <span />
        </span>
        <span className="compare-tag tag-before">Antes</span>
        <span className="compare-tag tag-after">Después</span>
        <input
          type="range"
          min={0}
          max={100}
          value={position}
          aria-label="Desliza para comparar antes y después"
          onChange={(e) => {
            setTouched(true)
            setPosition(Number(e.target.value))
          }}
        />
      </div>
      <figcaption>
        <strong>
          {DAMAGE_LABELS[issue.damageType]}
          {issue.locality && ` en ${issue.locality}`}
        </strong>
        {issue.example && <span className="badge badge-example">Ejemplo</span>}
        <span className="compare-reason">“{proof.reason}”</span>
        {issue.example?.credit && <span className="example-credit">{issue.example.credit}</span>}
        <a href={href('/dano', { id: issue.id })}>Ver el caso completo</a>
      </figcaption>
    </figure>
  )
}

function RulePlaceholder() {
  return (
    <div className="rule-placeholder" aria-hidden="true">
      <span className="rule-photo">
        <Icon name="camera" size={40} />
      </span>
      <span className="rule-check">
        <Icon name="checkCircle" size={28} />
      </span>
    </div>
  )
}

// ---------- Accountability ----------

function Accountability({ cards }: { cards: AgencyScorecard[] }) {
  const [ref, visible] = useInView<HTMLElement>(0.3)
  const sum = (key: keyof AgencyScorecard) => cards.reduce((total, c) => total + (c[key] as number), 0)
  return (
    <section ref={ref} className={`landing-section landing-agencies reveal${visible ? ' is-visible' : ''}`}>
      <header className="landing-section-header">
        <p className="landing-kicker">Rendición de cuentas</p>
        <h2>¿Quién responde y quién no?</h2>
        <p>Cada entidad, con sus peticiones vencidas y sus reparaciones puestas a prueba.</p>
      </header>
      <div className="landing-numbers is-compact">
        <Counter value={sum('overduePetitions')} label="peticiones vencidas sin respuesta" start={visible} tone="alert" />
        <Counter value={sum('repairClaims')} label="reparaciones declaradas" start={visible} />
        <Counter value={sum('claimsConfirmed')} label="confirmadas con foto" start={visible} tone="good" />
        <Counter value={sum('claimsRefuted')} label="desmentidas con foto" start={visible} tone="alert" />
      </div>
      <a className="button button-secondary landing-agencies-link" href={href('/entidades')}>
        Ver cada entidad
      </a>
    </section>
  )
}

// ---------- Final call ----------

function FinalCall() {
  const [ref, visible] = useInView<HTMLElement>(0.4)
  return (
    <section ref={ref} className={`landing-final reveal${visible ? ' is-visible' : ''}`}>
      <h2>Tu cuadra también cuenta.</h2>
      <p>Crear una cuenta toma un minuto. El mapa lo puede ver cualquiera, sin cuenta.</p>
      <div className="landing-actions">
        <a className="button button-primary button-lg landing-cta" href={href('/registro', { next: '/reportar' })}>
          Crear mi cuenta y reportar
        </a>
        <a className="button button-secondary button-lg" href={href('/mapa')}>
          Explorar el mapa
        </a>
      </div>
    </section>
  )
}
