# Plantillas de WhatsApp

Fuera de la ventana de 24 horas WhatsApp solo entrega **plantillas aprobadas**. Los avisos de cambio de estado
siempre se envían como plantilla (también dentro de la ventana), así funcionan igual en cualquier momento.

Estado: **pendientes de enviar a aprobación** en el Administrador de WhatsApp de Meta Business.

- Categoría: **Utilidad** (son avisos sobre algo que el ciudadano pidió, no publicidad).
- Idioma: **Español** (código `es`). Si se aprueban en otra variante, cambiar `WHATSAPP_TEMPLATE_LANGUAGE`.
- Los nombres y el orden de las variables deben coincidir con `IssueNotices` (backend). Si cambias uno, cambia el
  otro.
- Las variables no pueden llevar saltos de línea ni más de tres espacios seguidos: el backend las limpia.
- Sin aprobación, Meta rechaza el envío; el backend lo registra en el log y sigue (el correo de la web no depende
  de esto).

| Nombre | Se envía cuando | Variables |
|---|---|---|
| `aviso_peticion_radicada` | Se radica la petición (`FILED`) | 1 daño, 2 radicado, 3 fecha límite, 4 enlace |
| `aviso_entidad_respondio` | La entidad respondió sin decir que reparó (`AGENCY_RESPONDED`) | 1 entidad, 2 daño, 3 resumen, 4 enlace |
| `aviso_reparacion_reportada` | La entidad dice que reparó (`REPAIR_CLAIMED`) | 1 entidad, 2 daño |
| `aviso_reparacion_verificada` | Una foto confirma la reparación (`VERIFIED`) | 1 daño, 2 enlace |
| `aviso_dano_reabierto` | Una foto muestra que sigue dañado (`REOPENED`) | 1 daño, 2 enlace |
| `aviso_falta_foto` | La foto no fue concluyente (`NEEDS_MORE_EVIDENCE`) | 1 daño |

"Daño" es el tipo y la localidad, por ejemplo "Hueco en Chapinero".

## Textos

### `aviso_peticion_radicada`

```
Se radicó una petición por el daño que sigues: {{1}}. Radicado {{2}}. La entidad tiene hasta el {{3}} para responder. Síguelo aquí: {{4}}
```

Ejemplo: Hueco en Chapinero · SIM-2026-000012 · 19 de noviembre · https://civic.jesusflorez.cloud/#/dano?id=…

### `aviso_entidad_respondio`

```
{{1}} respondió sobre el daño que sigues ({{2}}): {{3}} Síguelo aquí: {{4}}
```

Ejemplo: Unidad de Mantenimiento Vial · Hueco en Chapinero · La entidad dice que la reparación está programada para diciembre. · https://civic.jesusflorez.cloud/#/dano?id=…

### `aviso_reparacion_reportada`

```
{{1}} dice que ya reparó el daño que sigues: {{2}}. Solo lo damos por reparado cuando una foto lo confirma. Si pasas por el lugar, responde a este mensaje con una foto actual. Escribe BAJA si no quieres más avisos.
```

Ejemplo: Unidad de Mantenimiento Vial · Hueco en Chapinero

Después de este aviso, la siguiente foto que envíe el ciudadano (durante 7 días) se toma como foto de verificación.

### `aviso_reparacion_verificada`

```
¡Reparación verificada! Una foto ciudadana confirmó que el daño que sigues ({{1}}) quedó reparado. Gracias por ayudar. Mira el antes y después: {{2}}
```

Después de este aviso se borra el número cifrado de ese daño.

### `aviso_dano_reabierto`

```
Una foto muestra que el daño que sigues ({{1}}) no está reparado, aunque la entidad dijo que sí. Lo reabrimos para volver a exigir la reparación: {{2}}
```

### `aviso_falta_foto`

```
No pudimos confirmar con la última foto si el daño que sigues ({{1}}) quedó reparado. Si pasas por el lugar, responde con otra foto desde un ángulo parecido al original.
```
