# civic-repairs (working name)

Citizens of Bogotá report urban damage (potholes, open manholes, damaged poles, risky trees) through **WhatsApp** or the web, with a photo, voice note or text and a location pin. **Muse Spark** interprets each report and **SAM 3.1** segments the damage and blurs faces and plates. Reports of the same spot are grouped into a public **Issue** on a map, and an Issue is only marked as repaired when a **new photo proves it**. An agency's word is never enough.

Built for the Meta Global AI Developer Hackathon.

- Live: <https://civic.jesusflorez.cloud> (soon)
- Project guide and architecture: [CLAUDE.md](CLAUDE.md)
- Meta Model API notes: [docs/meta-api.md](docs/meta-api.md)

## Local development

```bash
cp .env.example .env    # fill in the values
docker compose -f infra/docker-compose.yml --env-file .env up -d   # PostgreSQL + PostGIS on 127.0.0.1:5433
```

Module commands are listed in [CLAUDE.md](CLAUDE.md#comandos).

## Evaluation

Metrics on a labeled set of real photos from Bogotá: coming soon.
