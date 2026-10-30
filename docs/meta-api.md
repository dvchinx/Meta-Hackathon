# Meta Model API

Notas de lo que necesitamos de la API. Lo marcado como **probado** se verificó con llamadas reales el 2026-10-26; el resto viene de la documentación oficial ([dev.meta.ai/docs](https://dev.meta.ai/docs/models)).

## Básico

- Base URL: `https://api.meta.ai/v1`. Autenticación: `Authorization: Bearer $META_API_KEY`.
- Tres protocolos compatibles: Responses (`/v1/responses`), Chat Completions (`/v1/chat/completions`) y Messages (`/v1/messages`).
- `GET /v1/models` lista los modelos y no consume créditos (**probado**).

## Modelos que usamos

| Uso | Modelo | Endpoint | Precio |
|---|---|---|---|
| Interpretar reportes, leer respuestas de entidades, verificar antes/después | `muse-spark-1.3` | Chat Completions o Responses | $1.25 / 1M tokens de entrada, $4.25 / 1M de salida, $0.15 / 1M en caché |
| Segmentar daño, caras y placas | `sam-3.1` | `POST /v1/responses` | $2.50 por 1.000 imágenes |
| Transcribir audio (solo si hace falta) | `muse-voice-transcribe-1.0` | `POST /v1/asr/transcribe` | $0.18 por hora de audio |

- Muse Spark acepta **texto, imagen, video, audio y PDF** como entrada. No hace falta un modelo aparte para el audio de WhatsApp.
- **No usar los modelos `-contributor`**: cuestan unas 12 veces menos, pero Meta usa el contenido para entrenar, y nuestras fotos traen caras, placas y datos de ciudadanos.
- No usamos `muse-image-1.0` ni `muse-wonder-1.0`.
- Límites: 3.000 RPM tanto para Muse Spark como para SAM. No son un problema para nosotros.

## Muse Spark: detalles probados

- Incluso con `reasoning_effort: "minimal"` el modelo gasta tokens razonando (unos 20 en un prompt trivial). Si `max_tokens` es muy bajo, la respuesta llega con `content: null` y `finish_reason: "length"`. Hay que dejar margen: `max_tokens` ≥ 300 para respuestas cortas, y más para el JSON de interpretación.
- `usage` trae `prompt_tokens`, `completion_tokens`, `completion_tokens_details.reasoning_tokens` y `prompt_tokens_details.cached_tokens`. Con eso se calcula el costo de cada llamada.
- **Salidas estructuradas probadas:** `response_format: {type: "json_schema", json_schema: {name, strict: true, schema}}` en Chat Completions devuelve JSON válido contra el esquema. Igual validamos de nuestro lado (`MuseOutputParser`).
- Usamos `max_completion_tokens` (`max_tokens` está deprecado) y `reasoning_effort: "low"`; `"none"` da 400 porque Muse Spark siempre razona.
- Imágenes: parte `{"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}}`. Una imagen de ~1024 px son unos 1.300–1.500 tokens.
- **Costo medido de una interpretación** (foto de 960 px + texto): 1.526 tokens de entrada y 464 de salida, unos **$0.0039**.

## SAM 3.1: detalles probados

Petición (imagen en base64; **probado** con una foto de un bache de 960×720):

```json
{
  "model": "sam-3.1",
  "input": [{
    "type": "message",
    "role": "user",
    "content": [
      { "type": "input_text", "text": "pothole" },
      { "type": "input_image", "image_url": "data:image/jpeg;base64,..." }
    ]
  }],
  "stream": false,
  "store": false,
  "metadata": { "include_confidence": "true", "mask_encoding": "one_bit" }
}
```

- **Un concepto por llamada** (frase nominal corta: `"pothole"`, `"face"`, `"license plate"`). Daño, caras y placas son tres llamadas: unos $0.0075 por foto.
- Enviar siempre `store: false`: por defecto la API guarda la respuesta (`"store": true`).
- `usage.images_processed` cuenta las imágenes cobradas. `input_tokens` y `output_tokens` vienen en 0.
- La salida viene en `output[].content[].text`, con una línea por fotograma:

  ```text
  <0f>0<|box;x1=380;y1=402;x2=572;y2=534;w=960;h=720|><|mask;x=0;y=0;data=133,193,~<base85>|>,1<|box;...|><|mask;...|>
  ```

  - `<Nf>`: índice del fotograma. Le siguen registros `id<|box|><|mask|>` separados por comas. Los ids pueden no ser consecutivos.
  - La caja está en píxeles de la imagen original. `w` y `h` son el tamaño de esa imagen.
  - La máscara es `data=H,W,<enc><payload>`, con `~` (sin pérdida) o `!` (1 bit). El raster está recortado a la caja y se coloca en `(x1, y1)` (confirmado visualmente sobre la foto).
  - El payload puede contener `;` y `,`: hay que leer `data=` como el último campo.
  - La confianza `c` solo aparece si se pide `include_confidence`.
  - Guía del formato: <https://dev.meta.ai/docs/sam/reading-segmentation>
- **Decodificar con la librería oficial `meta-sam-parser`** (PyPI, de Meta). No decodificar el formato a mano: su licencia prohíbe la ingeniería inversa de los componentes de SAM. vision-service la usa en `app/sam/masks.py`.
- Pedimos `metadata.mask_encoding: "one_bit"` (más pequeño; solo necesitamos máscaras binarias). Probado de punta a punta.
- **Las máscaras de instancias no se solapan:** en la prueba del bache, SAM separó el hueco profundo y el área desgastada alrededor, y cada píxel pertenece a un solo objeto. El área del daño es la **unión** de todas las detecciones.
- **SAM es sensible a la re-codificación cerca de su umbral:** la misma foto devolvió el área desgastada alrededor del bache (c≈0,66) al enviarla tal cual, y no la devolvió con una copia re-codificada a calidad 85. vision-service manda los JPEG que no necesitan reducción byte por byte. Para antes/después, ambas fotos deben pasar por el mismo camino.
- **Conceptos por tipo de daño** (`DAMAGE_CONCEPTS` en `vision-service/app/analysis.py`), probados el 2026-10-28 con los 15 daños de ejemplo (`infra/seed/`) y el 2026-10-29 con la evaluación (`eval/`, fotos reales):
  - Detectan el daño: `"pothole"` (0,8–2 % de la foto), `"open manhole"`, `"utility pole"`, `"tree"`, `"street light"`, `"garbage pile"` (9–12 %), `"bench"`. `"storm drain"` detecta la rejilla, pero muy pequeña (0,04 %).
  - **Fugas:** `"water puddle"`, `"puddle"`, `"water"`, `"flowing water"` y `"water leak"` no detectan nada. `"wet pavement"` sí: 31 % de la foto con la fuga y 0 % con el andén seco. Con lluvia marcará toda la calle: una foto de verificación de un día lluvioso bloquea el "reparado" y pide otra foto.
  - **Andenes:** ninguna frase encuentra el daño (`"cracked sidewalk"`, `"broken pavement"`, `"broken sidewalk"`, `"crack"`, `"rubble"`, `"broken paving slabs"`, `"damaged sidewalk"`). `"hole in the ground"` encontró el hueco del andén en una foto (0,2 %) y no en la siguiente, donde se ve más grande. Se usa `"sidewalk"`, que marca el andén entero, y los andenes no comparan área en la verificación.
  - **Paraderos de bus:** `"bench"` no los encuentra (3 de 3 en la evaluación con área 0).
  - En la evaluación SAM encontró el daño en 30 de 39 reportes aceptados (`eval/README.md`).
  - Si se cambia una frase, subir `VisionServiceAdapter.VERSION`: es parte de la clave de la caché.
- **`verify-repair`:** con una foto de la misma avenida tomada desde otro ángulo, sin el punto del hueco a la vista, Muse respondió `sameLocation: true`, `damageSpotVisible: true` y "reparado" con 0,78–0,82, aunque el prompt v2 pide ubicar el punto con referencias fijas. Los "reparado" correctos estuvieron entre 0,93 y 0,98. Por eso `RepairCheck` exige 0,85 para "reparado".
- `"face"` también detecta caras de animales (en una prueba difuminó la cara de un burro). Difuminar de más es aceptable; lo peligroso sería difuminar de menos.

## Costo estimado por reporte

| Paso | Llamadas | Costo aprox. |
|---|---|---|
| Interpretación (foto + texto) | 1 × Muse Spark | ~$0.004 |
| Segmentación (daño, cara, placa) | 3 × SAM | $0.0075 |
| **Total por reporte** | | **~$0.012** |

Una verificación antes/después cuesta algo parecido. Con un tope de $120 alcanzan unos 10.000 reportes. Para la demo sobra; el riesgo real son los bucles o reintentos sin caché, y por eso existen el tope duro y la caché obligatoria.
