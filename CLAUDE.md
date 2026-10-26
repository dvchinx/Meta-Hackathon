# CLAUDE.md

> Nombre del proyecto: **por definir**. Usar `civic-repairs` como nombre técnico provisional (paquetes, servicios, base de datos, imágenes Docker). Dominio de producción: `civic.jesusflorez.cloud`.

## Qué es

Plataforma para que ciudadanos de Bogotá reporten daños urbanos por **WhatsApp** o una **app web**, con fotos o video, audio o texto con el contexto, y ubicación.

- **Muse Spark** interpreta el reporte (tipo de daño, urgencia, validez, pistas de ubicación) y lee las respuestas de las entidades.
- **SAM 3.1** segmenta el daño, las caras y las placas. Con esas máscaras estimamos el área, difuminamos y comparamos fotos de antes y después.
- Los reportes del mismo punto se agrupan en un **Issue** que aparece en un mapa público con contador, tiempo abierto, entidad responsable y estado.
- **Regla central del producto:** un Issue solo pasa a `VERIFIED` cuando una foto nueva confirma la reparación. Que la entidad declare que lo arregló **nunca** basta.

Proyecto para el Meta Global AI Developer Hackathon (10 días). Priorizar un flujo completo que funcione sobre funcionalidades extra.

## Alcance del MVP

- Ciudad: **solo Bogotá**.
- Tipos de daño: `POTHOLE`, `OPEN_MANHOLE`, `DAMAGED_POLE`, `RISKY_TREE`. Cualquier otro → `OTHER`, con revisión manual.
- Las entidades se simulan: la radicación real de peticiones queda fuera de alcance. Generamos el PDF del derecho de petición y un panel de administración simula las respuestas.
- Prioridad si hay que recortar (lo primero se recorta primero): video → formulario web de reporte → PDF de petición → ranking de entidades.
- **Nunca se recorta:** flujo de WhatsApp, mapa, verificación antes/después.

## Estructura del repositorio

```text
backend/         Spring Boot 4.1 (Java 21), arquitectura hexagonal
vision-service/  Python + FastAPI, solo CPU: cliente de SAM 3.1 (Meta API), difuminado, área, comparación antes/después
web/             React + TypeScript + Vite + MapLibre (mapas de OpenStreetMap)
infra/           docker-compose de desarrollo (PostGIS) y de producción
eval/            Conjunto de evaluación (fotos etiquetadas) y scripts de métricas
docs/            Arquitectura, decisiones, guion de la demo, notas de la Meta API (docs/meta-api.md)
.github/workflows/  CI y despliegue
deploy/          NO es parte del repo (está en .gitignore): copia local de la carpeta de despliegue del VPS, compartida con otros proyectos
```

## Comandos

> TODO: completar cuando exista cada módulo. Mantener esta sección actualizada.

```bash
# Infraestructura local (PostgreSQL + PostGIS en 127.0.0.1:5433)
docker compose -f infra/docker-compose.yml --env-file .env up -d

# Backend
cd backend && ./mvnw spring-boot:run
cd backend && ./mvnw verify          # tests + ArchUnit (Testcontainers: necesita Docker)

# Vision service
cd vision-service && uvicorn app.main:app --reload --port 8001
cd vision-service && pytest

# Web
cd web && npm run dev
cd web && npm run lint && npm run typecheck && npm test

# Evaluación (gasta créditos: ver "Presupuesto de créditos")
cd eval && python run_eval.py --subset small

# Stack completo de producción en local (para probar las imágenes)
docker compose -f infra/docker-compose.prod.yml --env-file .env up --build
```

## Backend: arquitectura hexagonal

```text
com.civicrepairs
├── domain/            Entidades, value objects, máquina de estados, reglas. SIN Spring, SIN JPA.
├── application/       Casos de uso (un servicio por caso de uso) y puertos (interfaces)
│   ├── port/in/       ReportDamageUseCase, ConfirmReportUseCase, RegisterAgencyResponseUseCase, VerifyRepairUseCase...
│   └── port/out/      VisionPort, LanguageModelPort, MessagingPort, MediaStoragePort, IssueRepositoryPort, GeoQueryPort...
└── adapters/
    ├── in/web/        REST para la app web y el panel admin
    ├── in/whatsapp/   Webhook de WhatsApp Cloud API
    └── out/           muse/, vision/, whatsapp/, persistence/ (JPA + PostGIS), storage/ (sistema de archivos), pdf/
```

- `domain` no importa nada de `adapters` ni de frameworks. **ArchUnit lo hace cumplir**: si un test de ArchUnit falla, se corrige el código, no el test.
- Cada puerto de salida tiene un **fake en memoria** para tests y desarrollo sin conexión.
- Comunicación interna con eventos de Spring + tabla **outbox**. No usar Kafka.
- Migraciones con **Flyway**, que se aplican al arrancar el backend.

## Modelo de dominio

- **Report:** un envío individual de un ciudadano (medios, contexto, ubicación, salida de los modelos).
- **Issue:** un daño físico real; agrupa uno o más Reports. Es lo que aparece en el mapa.
- **Media:** foto, video o audio. Guardamos el original privado y una versión pública difuminada.
- **Agency:** entidad responsable (por tipo de daño, con reglas configurables).
- **StatusTransition:** historial auditable de cada cambio de estado (quién o qué lo causó, evidencia, fecha).

### Estados del Issue

```text
REPORTED → FILED → AGENCY_RESPONDED → REPAIR_CLAIMED → VERIFIED
                                                     ↘ REOPENED → (vuelve a FILED)
Estado auxiliar: NEEDS_MORE_EVIDENCE (la verificación no fue concluyente)
```

- Las transiciones permitidas están en `domain` y se prueban con tests unitarios.
- `VERIFIED` y `REOPENED` solo se alcanzan con evidencia fotográfica procesada por `VerifyRepairUseCase`.
- Toda transición crea un `StatusTransition`. Nada se borra.

## Pipeline de IA

Todos los modelos de Meta se consumen por la **Meta Model API** con API key (`muse-spark-1.3` y `sam-3.1`). No alojamos modelos ni usamos GPU. Endpoints, formato de las máscaras, precios y particularidades probadas: **leer `docs/meta-api.md` antes de tocar un adaptador de Meta.**

1. **Interpretación (Muse Spark):** devuelve JSON validado contra un esquema:
   `{ damageType, urgency, isValidUrbanDamage, locationHints, summary, confidence }`.
   Si `isValidUrbanDamage=false` o `confidence < 0.6`, el bot pide otra foto en lugar de registrar.
   Muse Spark acepta audio directamente: las notas de voz de WhatsApp van en la misma llamada, sin transcripción aparte.
2. **Segmentación (SAM 3.1 vía API, orquestado por vision-service):** una llamada por concepto (daño, `face`, `license plate`). vision-service calcula localmente el área estimada (m²) y genera la versión difuminada.
3. **Confirmación:** el bot envía el resumen con la imagen marcada. Solo después de que el usuario confirma se crea o actualiza el Issue.
4. **Deduplicación:** `ST_DWithin` a 30 m + mismo `damageType` + similitud visual sobre un umbral configurable → se suma al Issue existente.
5. **Respuesta de la entidad (Muse Spark):** clasifica la respuesta en `RESOLVES`, `EVASIVE` o `NO_RESPONSE`.
6. **Verificación antes/después:** proporción de áreas de las máscaras de SAM + juicio de Muse Spark con ambas imágenes superpuestas. Si no coinciden → `NEEDS_MORE_EVIDENCE`.

Reglas:

- Los prompts viven en archivos versionados (`backend/src/main/resources/prompts/`), no incrustados en el código.
- **Caché obligatoria:** clave = hash del medio + versión del prompt (o de los parámetros de SAM). Nunca llamar dos veces al modelo con la misma entrada. La caché vive en PostgreSQL, así sobrevive a reinicios y despliegues.
- **Registrar el costo** (tokens o créditos) de cada llamada en una tabla de uso, incluidas las que hace vision-service (las reporta en su respuesta y el backend las guarda).
- Validar toda salida de los modelos contra su esquema. Si no valida, reintentar una vez y luego marcar el Report para revisión manual.
- Enviar siempre `store: false` en la Responses API, y nunca usar modelos `-contributor`: Meta entrena con ese contenido y nuestras fotos traen datos de ciudadanos.
- Muse Spark razona incluso con `reasoning_effort: "minimal"`: dejar margen en `max_tokens` o la respuesta llega vacía.

### Presupuesto de créditos

Tenemos **$150 en créditos para todo el hackathon**: desarrollo, evaluación y demo. Un reporte cuesta unos $0.012 (1 llamada a Muse Spark + 3 a SAM), así que el riesgo no es el uso normal, sino los bucles, los reintentos y las llamadas sin caché.

- Por defecto todo corre con los **fakes**. Las llamadas reales solo ocurren con `META_API_ENABLED=true`. CI nunca la activa.
- **Tope duro:** `META_BUDGET_LIMIT_USD` (por defecto 120, para reservar créditos a la demo). Al alcanzarlo, el adaptador deja de llamar a la API y los Reports pasan a revisión manual. El panel admin muestra el gasto acumulado.
- Antes de llamar a la API, filtrar todo lo que se pueda gratis: límite por número, medio repetido (hash), formato no soportado, mensaje sin foto.
- Reducir las imágenes (lado máximo 1024 px, JPEG) antes de enviarlas.
- Video: extraer 1 a 3 fotogramas con ffmpeg. Nunca enviar el video completo.
- SAM: no pedir caras ni placas si Muse Spark ya indicó que la foto no tiene personas ni vehículos.
- Iterar prompts con un subconjunto pequeño de `eval/`. La evaluación completa se corre solo para las métricas finales.

## WhatsApp

- Webhook **idempotente**: deduplicar por `message.id`, porque WhatsApp reintenta las entregas.
- Validar la firma `X-Hub-Signature-256` de cada petición con el app secret.
- El bot acepta los datos **en cualquier orden** y en un mismo mensaje. Solo pide lo que falta.
- Ubicación: pin de WhatsApp que el usuario puede mover. No depender del GPS de la foto, porque WhatsApp elimina esos metadatos.
- Fuera de la ventana de 24 horas solo se envían **plantillas aprobadas**. Los textos de las plantillas viven en `docs/whatsapp-templates.md`.
- Los mensajes al usuario van en español colombiano, cortos y claros.
- URL del webhook en producción: `https://civic.jesusflorez.cloud/webhooks/whatsapp`. En local se usa un túnel (cloudflared o ngrok).

## Privacidad y abuso

- Nunca exponer números de teléfono: se guardan con hash y sal. La API pública nunca devuelve datos del reportante.
- El mapa y la API pública **solo** sirven la versión difuminada de los medios. Originales y difuminados se guardan en directorios distintos del volumen de medios, y el backend solo sirve el público.
- Límite de reportes por número por día (configurable).
- Secretos solo en variables de entorno o `.env` local (en `.gitignore`). Nunca en el repo ni en los workflows.

## Despliegue

Producción en el VPS **jesusflorez.cloud**. GitHub Actions construye las imágenes, las sube a GHCR y despliega por SSH.

### Cómo está montado el VPS

- El VPS ya corre muchos proyectos en **un compose compartido** (carpeta `deploy/` en el VPS; hay una copia local en `deploy/`, fuera de git). Ahí viven el contenedor **`personal-nginx`** (puertos 80/443) y `certbot`, con **un solo certificado SAN** para todos los subdominios.
- Nuestro stack **no** entra en ese compose. Es un proyecto aparte (`/opt/civic-repairs/`, `infra/docker-compose.prod.yml`) con imágenes de GHCR, así CI solo toca lo nuestro.
- `civic-backend` y `civic-web` se unen a la red de `personal-nginx` (red externa, nombre configurable con `PROXY_NETWORK`). PostgreSQL y vision-service quedan solo en una red interna. **Ningún contenedor publica puertos en el host.**

```text
Internet → personal-nginx (TLS) → civic.jesusflorez.cloud
   /api/*, /webhooks/*, /media/*  → civic-backend:8080
   /*                             → civic-web:80 (Nginx con los estáticos de Vite)

Red interna civic: civic-backend, civic-vision, civic-postgres (PostGIS), volumen de medios
```

- Config de Nginx: `deploy/nginx/conf.d/civic.conf` (archivo propio, no se edita `default.conf`). Usa `resolver` + variable, así nginx arranca aunque nuestros contenedores no existan.
- TLS: `deploy/civic-add-ssl-domain.sh` agrega `civic` al certificado SAN sin detener la suite. Requiere el registro A apuntando al VPS.
- **Cualquier cambio en `deploy/` se sube a mano al VPS. Al terminar, avisar al usuario qué archivos de `deploy/` cambiaron.** Nunca agregar `deploy/` al repo: contiene claves TLS y secretos de otros proyectos.

### GitHub Actions

- `ci.yml` (pull requests y push): tests y linter por módulo, con filtros de rutas. Sin llamadas a Meta.
- `deploy.yml` (push a `main`, tras CI verde):
  1. Construye `ghcr.io/<owner>/civic-repairs-{backend,vision-service,web}` con etiquetas `sha-<commit>` y `latest` (`linux/amd64`).
  2. Copia `infra/docker-compose.prod.yml` a `/opt/civic-repairs/` en el VPS.
  3. Por SSH: `docker compose pull && docker compose up -d`, y luego comprueba la salud (`/actuator/health` del backend, `/health` de vision-service).
- Secretos de GitHub: `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`. Para subir a GHCR se usa `GITHUB_TOKEN`. Los secretos de la aplicación viven solo en `/opt/civic-repairs/.env` del VPS, **no** en GitHub.
- Rollback: en el VPS, `IMAGE_TAG=sha-<anterior> docker compose up -d`.
- Datos persistentes en volúmenes con nombre (`pgdata`, `media`). Nunca usar `docker compose down -v` en producción.
- El VPS ya tiene mucha carga (Kafka, MySQL, varios Spring Boot). Ponerles límite de memoria a nuestros contenedores (`mem_limit`, `-XX:MaxRAMPercentage`).

## Convenciones

- Código, identificadores y commits en **inglés**. Textos para el usuario en **español**.
- Java 21, Spring Boot 4.1 (Jackson 3, starters modulares como `spring-boot-starter-webmvc`), records para value objects y DTOs. Indentación con tabs, como generó Initializr.
- Commits con Conventional Commits (`feat:`, `fix:`, `test:`, `ci:`...).
- Tests: JUnit 5 + Testcontainers (PostGIS) en backend, pytest en vision-service, Vitest en web.
- **Los tests nunca llaman a las APIs reales de Meta.** Usar los fakes de los puertos y fixtures de `eval/`.
- Toda configuración por variables de entorno, documentadas en `.env.example` (sin valores reales).
- Cada servicio expone un endpoint de salud y tiene `Dockerfile` multi-stage.
- Antes de dar una tarea por terminada: correr los tests del módulo afectado y el linter.

## Evaluación

`eval/` contiene fotos reales de Bogotá etiquetadas (tipo, área aproximada, grupo de duplicados, reparado o no). Métricas que se reportan en el README:
precisión de clasificación, error del área, precisión de la deduplicación, acierto de la verificación y costo promedio por reporte.

## Qué NO hacer

- No marcar un Issue como resuelto porque la entidad lo dice.
- No agregar Kafka, microservicios extra ni un sistema de autenticación complejo (basta un token para el panel admin).
- No ampliar el alcance fuera de Bogotá ni de los 4 tipos de daño.
- No usar Muse Image: no aporta al producto.
- No alojar modelos ni agregar dependencias de GPU.
- No llamar a la API de Meta en tests, CI ni bucles de prueba sin caché.
- No modificar los tests de ArchUnit para que pasen.
- No exponer PostgreSQL ni vision-service a internet. No modificar los sitios de otros proyectos en `deploy/`.
- No agregar MinIO ni otro servidor S3: los medios van al sistema de archivos detrás de `MediaStoragePort`.

## Pendientes por confirmar

- [x] SAM 3.1 y Muse Spark disponibles en la Meta Model API (probado el 2026-10-26, ver `docs/meta-api.md`).
- [x] Muse Spark acepta audio directamente.
- [ ] Número de prueba de WhatsApp Cloud API y plantillas enviadas a aprobación.
- [ ] VPS: RAM libre real y nombre exacto de la red de `personal-nginx` (`docker network ls`).
- [ ] Registro A de `civic.jesusflorez.cloud` y ejecución de `deploy/civic-add-ssl-domain.sh`.
- [ ] Repositorio en GitHub; ¿paquetes de GHCR públicos o privados? (privados requieren `docker login ghcr.io` en el VPS con un token `read:packages`).
