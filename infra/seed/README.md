# Daños de ejemplo para la demo

Unos 15 Issues de ejemplo repartidos por localidades, tipos de daño y estados, para que el mapa no se vea vacío. Cada uno
parte de una **foto real de la calle** tomada de [Mapillary](https://www.mapillary.com) (CC BY-SA 4.0) en el punto exacto
del mapa. Con Higgsfield se le **agrega el daño** (foto del "antes") y, para los verificados o reabiertos, se genera el
"después" sobre esa misma imagen.

Los ejemplos pasan por el **flujo real**: Muse Spark interpreta la foto y el texto, SAM difumina y mide, la entidad se
asigna con las reglas de siempre, la petición se radica (simulada), Muse clasifica la respuesta de la entidad y compara el
antes y el después. Lo único inventado son las fotos, los textos y las fechas.

- Todo se corre contra el **stack local**, así en producción no se crean cuentas ni salen correos. Luego se exportan solo
  las filas del lote (`seed.sql`) y sus medios (`media.tar`) para importarlos en producción.
- Cada Issue lleva `seed_batch` (migración V10). La web le pone la etiqueta **"Ejemplo"**, con un aviso y el crédito de la
  foto base (Mapillary exige atribución). `purge.sql` borra un lote completo. Es la única excepción a "nada se borra":
  los datos reales nunca tienen `seed_batch`.
- Las cuentas `demo01…08@example.com` y los datos de peticionario son ficticios y solo existen en la base local. En
  producción las peticiones de ejemplo quedan sin cuenta (`filer_user_id` nulo) y con un radicado propio de producción.

## Archivos

| Archivo | Qué es |
| --- | --- |
| `scenes.json` | Las escenas: punto, tipo, estado final, textos del ciudadano y de la entidad, fechas (días atrás) y quién apoya |
| `images/<escena>/` | `base.jpg` (Mapillary), `before.jpg` y `after.jpg` (editadas). Se versionan |
| `seed.py` | `candidates`, `pick`, `run`, `reset`, `export` (ver `python seed.py --help`) |
| `purge.sql` | Borra un lote: `psql -v batch=demo-2026-10 -f purge.sql` |
| `work/`, `out/` | Candidatas de Mapillary, estado de la corrida y exportación. Fuera de git |

## Pasos

```bash
cd infra/seed && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
```

1. **Fotos base.** Con `MAPILLARY_TOKEN` en `.env` (gratis en mapillary.com/developer):
   `.venv/bin/python seed.py candidates` descarga hasta 8 fotos alrededor de cada punto en `work/candidates/<escena>/`.
   Se elige una por escena con `seed.py pick <escena> <id>`, que la copia a `images/<escena>/base.jpg` y mueve el punto
   de la escena a donde se tomó la foto.
2. **Editar con Higgsfield** cada `base.jpg` con el texto de `edit.before` de la escena → `before.jpg`; y `before.jpg` con
   `edit.after` → `after.jpg` (solo `VERIFIED` y `REOPENED`). Para `NEEDS_MORE_EVIDENCE` (`"after": "elsewhere"`) el
   "después" es otra foto de Mapillary de un lugar distinto, sin editar: Muse no reconoce el sitio y la verificación queda
   inconclusa.
3. **Correr el flujo en local** con los modelos reales (unos $0,015 por escena; la caché evita pagar dos veces):

   ```bash
   docker compose -f infra/docker-compose.yml --env-file .env up -d
   cd vision-service && META_API_ENABLED=true .venv/bin/uvicorn app.main:app --port 8001
   cd backend && META_API_ENABLED=true VISION_SERVICE_URL=http://127.0.0.1:8001 \
       PERSONAL_DATA_KEY=$(openssl rand -base64 32) ./mvnw spring-boot:run
   cd infra/seed && .venv/bin/python seed.py run
   ```

   `run` avisa si Muse ve otro tipo de daño, si la respuesta de la entidad se clasifica distinto o si la verificación no da
   lo esperado. Para repetir una escena: ajustar foto o textos, `seed.py reset <escena>` y `seed.py run <escena>`.
   Las fechas de cada paso se reescriben según `timeline` (días atrás), y el plazo de la petición se recalcula con los
   festivos de Colombia, igual que `ResponseDeadline`.
4. **Exportar:** `.venv/bin/python seed.py export` → `out/seed.sql` y `out/media.tar`.
5. **Importar en producción** (la V10 ya debe estar desplegada). Primero los archivos, así ninguna foto queda rota:

   ```bash
   scp infra/seed/out/seed.sql infra/seed/out/media.tar root@jesusflorez.cloud:/opt/civic-repairs/
   ssh root@jesusflorez.cloud
   cd /opt/civic-repairs
   docker exec -i civic-backend tar -xf - -C /data/media < media.tar
   docker exec -i civic-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < seed.sql
   ```

   Importar dos veces no duplica nada (`ON CONFLICT DO NOTHING`).

## Quitar los ejemplos

```bash
docker exec -i civic-postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v batch=demo-2026-10' < purge.sql
```

Borra también lo que ciudadanos reales hayan agregado a un ejemplo ("Yo también lo vi", peticiones, fotos de
verificación). Al final lista los archivos del volumen de medios que quedaron sin uso.
