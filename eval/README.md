# Evaluación

Fotos reales etiquetadas a mano que pasan por el **flujo real** del backend local: Muse Spark interpreta, SAM segmenta,
el Issue se crea o se une a otro, la entidad dice que reparó y una foto nueva lo confirma o lo desmiente. Las métricas
son las del producto, no las de cada modelo por separado.

## Qué hay

| Archivo | Qué es |
| --- | --- |
| `dataset.json` | Las fotos (fuente, autor, licencia) y las cuatro pruebas con sus etiquetas |
| `photos/` | 55 fotos reducidas a 1024 px. Se versionan |
| `collect.py` | Cómo se buscaron: Wikimedia Commons y Mapillary (ver `python collect.py --help`) |
| `run_eval.py` | Corre las pruebas contra el backend local y resume las métricas |
| `work/`, `results/` | Candidatas descartadas y resultados de cada corrida. Fuera de git |

**De dónde salen las fotos.** No hay un banco abierto de fotos de daños en Bogotá: en casi 300 zonas de la ciudad
recorridas en Mapillary, su detector marcó un solo hueco. Por eso:

- **Daños:** fotos de Wikimedia Commons de varios países (dominio público, CC0, CC BY o CC BY-SA), elegidas una por una
  mirando la foto. El tipo de daño no depende de la ciudad; la ubicación de Bogotá la pone la prueba.
- **Fotos sin daño:** 6 calles de Bogotá en Mapillary (CC BY-SA 4.0), más una tapa de alcantarilla intacta, una banca
  en buen estado y un farol de parque. Son los casos en que el bot debe pedir otra foto.
- **Pares antes/después reales:** dos fotos del mismo daño desde otro ángulo (sigue dañado), fotos de otro lugar, y una
  foto de la misma avenida del ejemplo de Kennedy tomada desde un ángulo donde no se ve el punto del hueco.
- **Pares sintéticos:** 5 de los daños de ejemplo de `infra/seed/` (foto de Mapillary editada con IA). Son los únicos
  "reparados": no encontramos pares reales de un daño antes y después de repararlo. Se reportan aparte.

## Las cuatro pruebas

| Prueba | Cómo | Acierto |
| --- | --- | --- |
| Clasificación (39 fotos, 9 tipos) | Reporte con la foto sola, sin texto | Muse lo acepta con el tipo etiquetado (dos faroles rotos admiten poste o luminaria) |
| Fotos sin daño (9) | Igual | El bot pide otra foto (`PHOTO_REJECTED`) |
| Deduplicación (9 pares) | Se confirma A y se reporta B a 5 m | B se une a A solo si es el mismo daño |
| Verificación (12 pares) | A se radica, la entidad dice que reparó y B es la foto de verificación | El resultado coincide; un "reparado" que no lo es cuenta como error inseguro |

Además mide en qué parte de los reportes aceptados SAM encontró el daño (área > 0) y el costo por reporte.

## Resultados (2026-10-29)

| Métrica | Resultado |
| --- | --- |
| Tipo de daño correcto, solo con la foto | **38/39 (97 %)** |
| Fotos sin daño rechazadas | **8/9 (89 %)** |
| SAM encontró el daño | 30/39 (77 %) |
| Deduplicación: mismo daño unido / daños distintos separados | 3/4 · **5/5** (precisión 100 %) |
| Verificación antes/después | **12/12** (pares reales 7/7) |
| Verificado como reparado sin estarlo (error inseguro) | **0** |
| Costo promedio por reporte (Muse Spark + 3 llamadas a SAM) | **$0,011** |

Los fallos:

- **Andén levantado por la raíz de un árbol → `RISKY_TREE`.** Es ambiguo: el daño es del andén, pero lo causa el árbol.
- **Farol de parque intacto, de día → aceptado como luminaria apagada.** Una foto de día no muestra si la lámpara
  prende; sin texto, Muse supone que es lo que se reporta. El ciudadano confirma el resumen antes de crear el reporte.
- **Poste de alumbrado inclinado visto de lejos → `OTHER`**, y por eso no se unió a la otra foto del mismo poste. La
  comparación de Muse no llegó a correr: solo se comparan reportes del mismo tipo.
- **SAM sin área (9 de 39):** los tres paraderos de bus (la frase de mobiliario es `"bench"`), dos fugas (un chorro
  y una de noche), un andén, un botadero, un árbol y un poste. La foto marcada sale sin resaltar; la clasificación no
  cambia. En fugas y basuras, sin área medida decide solo Muse (`RepairCheck.MIN_MEASURED_AREA`).

**Lo que cambió por esta evaluación.** En la primera corrida, la foto de la avenida de Kennedy tomada desde otro ángulo
salió **verificada como reparada**: Muse dijo que veía el punto y que el hueco ya no estaba (confianza 0,82). Los
"reparados" correctos tuvieron entre 0,93 y 0,98. Desde entonces `RepairCheck` exige 0,85 para dar un daño por reparado
(0,7 para reabrirlo, que es reversible), y si las reglas descartan el juicio de Muse se muestra un motivo neutro en
lugar del suyo. El prompt `verify-repair.v2` también pide ubicar el punto con referencias fijas, pero en esta foto Muse
siguió diciendo que lo veía: lo que lo frena es el umbral.

Con 55 fotos, cada fallo mueve varios puntos porcentuales. Son cifras para orientar, no un benchmark.

## Cómo correrla

Con el stack local levantado y los modelos reales, como en `infra/seed/README.md` (`META_API_ENABLED=true`, un
`PERSONAL_DATA_KEY` y `ADMIN_TOKEN` en `.env`):

```bash
cd eval && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python run_eval.py --subset small   # 13 casos, unos $0,20 la primera vez
.venv/bin/python run_eval.py --subset full    # todo, unos $0,55 la primera vez; escribe results/AAAA-MM-DD.json
```

Todo pasa por la caché del backend: repetir una corrida solo paga lo que cambió (un prompt nuevo, una foto nueva). Los
Issues que crea llevan `seed_batch='eval'` y se borran al empezar y al terminar. Usa cuentas
`eval-<fecha>-NN@example.com` que solo existen en la base local.
