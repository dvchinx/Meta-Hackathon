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
- Admite salidas estructuradas (JSON schema), llamadas a funciones y caché de prompts.

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
  "store": false
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
  - La máscara es `data=H,W,<enc><payload>`, con `~` (sin pérdida) o `!` (1 bit), y el payload en **base85**. **Observado:** H×W coincide con el tamaño de la caja (133×193 para una caja de 192×132), así que el raster está recortado a la caja y se coloca en `(x1, y1)`. Hay que confirmarlo al implementar el decodificador.
  - El payload puede contener `;` y `,`: hay que leer `data=` como el último campo.
  - La confianza `c` solo aparece si se pide `include_confidence`.
  - Guía del formato: <https://dev.meta.ai/docs/sam/reading-segmentation>
- Puede devolver varias detecciones anidadas para el mismo daño (en la prueba devolvió el bache y una región más amplia a su alrededor). Para el área hay que definir una regla, por ejemplo la máscara de mayor confianza o la unión de las máscaras.

## Costo estimado por reporte

| Paso | Llamadas | Costo aprox. |
|---|---|---|
| Interpretación (foto + texto) | 1 × Muse Spark | ~$0.004 |
| Segmentación (daño, cara, placa) | 3 × SAM | $0.0075 |
| **Total por reporte** | | **~$0.012** |

Una verificación antes/después cuesta algo parecido. Con un tope de $120 alcanzan unos 10.000 reportes. Para la demo sobra; el riesgo real son los bucles o reintentos sin caché, y por eso existen el tope duro y la caché obligatoria.
