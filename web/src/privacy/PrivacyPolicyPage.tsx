import { href } from '../routing'
import { Icon } from '../ui/Icon'
import { PRIVACY_CONTACT, PRIVACY_POLICY_VERSION } from './policy'

const effective = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })

/** Política de tratamiento de datos personales (Ley 1581 de 2012, Decreto 1377 de 2013). */
export function PrivacyPolicyPage() {
  const mail = <a href={`mailto:${PRIVACY_CONTACT}`}>{PRIVACY_CONTACT}</a>
  return (
    <div className="page">
      <header className="page-header">
        <a className="back-link" href={href('/')}>
          <Icon name="arrowLeft" size={16} />
          Volver al mapa
        </a>
        <h1>Política de tratamiento de datos personales</h1>
        <p>
          Versión {PRIVACY_POLICY_VERSION} · Vigente desde el {effective.format(new Date(`${PRIVACY_POLICY_VERSION}T00:00:00Z`))}
        </p>
      </header>

      <article className="card legal">
        <section>
          <h2>En pocas palabras</h2>
          <ul>
            <li>Para reportar solo pedimos un nombre para mostrar, un correo y una contraseña.</li>
            <li>
              Tus datos de identificación (documento, nombres, apellidos, celular) son <strong>opcionales</strong>. Solo
              los pedimos si decides radicar una petición, los guardamos cifrados y{' '}
              <strong>solo los compartimos con la entidad ante la que radicas, en el momento de radicar</strong>.
            </li>
            <li>El mapa es público, pero nunca muestra quién reportó ni quién radicó.</li>
            <li>Difuminamos caras y placas antes de publicar cualquier foto.</li>
            <li>No vendemos tus datos ni los usamos para publicidad.</li>
          </ul>
        </section>

        <section>
          <h2>1. Responsable del tratamiento</h2>
          <p>
            Equipo de Daños urbanos, un prototipo desarrollado para el Meta Global AI Developer Hackathon. Domicilio:
            Bogotá D.C., Colombia. Correo de contacto para todo lo relacionado con tus datos: {mail}.
          </p>
          <p>
            Daños urbanos es un prototipo: <strong>todavía no radica peticiones reales</strong>. La radicación es una
            simulación y tus datos no se envían a ninguna entidad. Si eso cambia, actualizaremos esta política y te
            pediremos autorización de nuevo.
          </p>
        </section>

        <section>
          <h2>2. Qué datos tratamos</h2>
          <dl className="legal-list">
            <dt>Cuenta web</dt>
            <dd>Nombre para mostrar, correo electrónico y contraseña (guardamos solo su huella cifrada con BCrypt).</dd>
            <dt>WhatsApp</dt>
            <dd>
              Tu número no se guarda: lo convertimos en un código irreversible (HMAC con sal) que solo sirve para
              reconocer tus reportes. Los mensajes se procesan en memoria.
            </dd>
            <dt>Reportes</dt>
            <dd>
              Fotos, el texto que escribas y la ubicación del daño. La foto original es privada; al mapa solo llega una
              copia con caras y placas difuminadas.
            </dd>
            <dt>Datos para radicar (opcionales)</dt>
            <dd>
              Tipo y número de documento, nombres, apellidos, celular y, si quieres, una dirección. Son los datos que
              piden las entidades para tramitar un derecho de petición (Ley 1755 de 2015). Los guardamos cifrados
              (AES-256) y puedes borrarlos cuando quieras.
            </dd>
          </dl>
          <p>
            No pedimos datos sensibles. Si una foto capta a una persona, la difuminamos antes de publicarla y la
            original solo se usa para analizar el daño. Si eres menor de edad, usa la plataforma con autorización de
            tu madre, padre o representante legal.
          </p>
        </section>

        <section>
          <h2>3. Para qué los usamos</h2>
          <ul>
            <li>Crear tu cuenta, iniciar sesión y confirmar tu correo.</li>
            <li>Analizar tus fotos para identificar el tipo de daño, su urgencia y la entidad responsable.</li>
            <li>Publicar el daño en el mapa (sin tus datos) y agrupar los reportes del mismo punto.</li>
            <li>Avisarte del avance de tus reportes.</li>
            <li>
              Solo si lo autorizas en cada caso: armar el derecho de petición a tu nombre y entregarlo a la entidad
              responsable de ese daño.
            </li>
            <li>Medir la calidad del servicio con datos agregados, sin identificarte.</li>
          </ul>
        </section>

        <section>
          <h2>4. Con quién los compartimos</h2>
          <p>
            <strong>Entidades públicas:</strong> solo cuando radicas una petición, y solo con la entidad ante la que
            radicas (por ejemplo, la Unidad de Mantenimiento Vial para un hueco). Les llega lo que exige la ley para
            tramitarla: tus datos de identificación, tu correo para la respuesta, la descripción, la ubicación y la
            foto difuminada del daño. Nunca compartimos tus datos con otras entidades ni con otros ciudadanos.
          </p>
          <p>
            <strong>Proveedores que procesan datos por cuenta nuestra (encargados):</strong> Meta (análisis de fotos
            con sus modelos de IA, con la opción de no conservar el contenido, y la API de WhatsApp), Google (inicio de
            sesión, si lo eliges), Resend (envío de correos) y el proveedor del servidor donde funciona la plataforma.
            Algunos están fuera de Colombia; solo reciben lo necesario para prestar su servicio.
          </p>
        </section>

        <section>
          <h2>5. Qué es público</h2>
          <p>
            El mapa muestra el tipo de daño, su ubicación, la foto difuminada, el número de reportes, la entidad
            responsable, el número de radicado y el plazo de respuesta. Nunca muestra tu nombre, correo, número de
            teléfono ni documento.
          </p>
        </section>

        <section>
          <h2>6. Cuánto tiempo los guardamos</h2>
          <ul>
            <li>Datos de la cuenta: mientras tengas la cuenta.</li>
            <li>Datos para radicar: hasta que los borres desde «Mis datos para radicar».</li>
            <li>
              Copia de los datos incluida en cada petición radicada: mientras el caso siga abierto y el tiempo necesario
              para demostrar qué se radicó. Borrar tus datos para radicar no borra las peticiones ya radicadas.
            </li>
          </ul>
        </section>

        <section>
          <h2>7. Tus derechos</h2>
          <p>Como titular puedes, de forma gratuita:</p>
          <ul>
            <li>Conocer, actualizar y rectificar tus datos.</li>
            <li>Pedir prueba de la autorización que nos diste.</li>
            <li>Saber cómo hemos usado tus datos.</li>
            <li>Revocar la autorización o pedir que borremos tus datos, cuando no exista un deber legal de conservarlos.</li>
            <li>Presentar quejas ante la Superintendencia de Industria y Comercio, después de haber acudido a nosotros.</li>
          </ul>
        </section>

        <section>
          <h2>8. Cómo ejercerlos</h2>
          <p>
            Escríbenos a {mail} desde el correo de tu cuenta, diciendo qué necesitas. Respondemos las consultas en
            máximo 10 días hábiles (prorrogables 5 más) y los reclamos en máximo 15 días hábiles (prorrogables 8 más),
            como indica la Ley 1581 de 2012. Tus datos para radicar los puedes ver, corregir o borrar tú mismo en{' '}
            <a href={href('/mis-datos')}>Mis datos para radicar</a>.
          </p>
        </section>

        <section>
          <h2>9. Seguridad</h2>
          <p>
            Usamos conexiones cifradas (HTTPS), guardamos las contraseñas con BCrypt y los datos para radicar con
            cifrado AES-256, y separamos las fotos originales de las públicas. Ningún sistema es infalible: si
            detectamos un incidente que afecte tus datos, te avisaremos.
          </p>
        </section>

        <section>
          <h2>10. Cambios a esta política</h2>
          <p>
            Si la cambiamos, publicaremos la nueva versión aquí y te pediremos aceptarla de nuevo antes de guardar o
            compartir tus datos para radicar.
          </p>
        </section>
      </article>
    </div>
  )
}
