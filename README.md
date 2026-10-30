# civic-repairs (working name)

Citizens of Bogotá report urban damage (potholes, open manholes, damaged poles, risky trees, street lights, water leaks, blocked drains, garbage, sidewalks and street furniture) on the web, with a photo, a short text and a location pin. **Muse Spark** interprets each report and **SAM 3.1** segments the damage and blurs faces and plates. Reports of the same spot are grouped into a public **Issue** on a map, and an Issue is only marked as repaired when a **new photo proves it**. An agency's word is never enough. The photo also decides the responsible agency (Muse Spark reads the markings on lids and poles), and a citizen can file a derecho de petición with their own data, which is stored encrypted and shared only with that agency (filing is simulated for now). A public **Agencies** page shows, for each agency, the petitions left unanswered past the legal deadline, the evasive replies, and how many of its claimed repairs a photo confirmed or disproved.

A WhatsApp channel was built (webhook, bot, status notices) but Meta did not approve the WhatsApp Business account, so the web app is the only channel.

Built for the Meta Global AI Developer Hackathon.

- Live: <https://civic.jesusflorez.cloud>
- Project guide and architecture: [CLAUDE.md](CLAUDE.md)
- Meta Model API notes: [docs/meta-api.md](docs/meta-api.md)

## Local development

```bash
cp .env.example .env    # fill in the values
docker compose -f infra/docker-compose.yml --env-file .env up -d   # PostgreSQL + PostGIS on 127.0.0.1:5433
```

Module commands are listed in [CLAUDE.md](CLAUDE.md#comandos).

## Demo examples

The live map includes about 15 example damages labeled **"Ejemplo"**: real street photos from Mapillary (CC BY-SA 4.0) with the damage added by AI, run through the real pipeline (Muse Spark, SAM 3.1, routing, simulated petition, agency reply and before/after check). Citizen reports never carry that label, and one script removes the examples. See [infra/seed/README.md](infra/seed/README.md).

## Evaluation

55 real photos, labeled by hand, run through the real pipeline on the local stack (Muse Spark, SAM 3.1, dedup, petition, agency reply, before/after check). Damage photos come from Wikimedia Commons; the photos with no damage are Bogotá streets from Mapillary. The only "repaired" pairs are 5 AI-edited demo examples, reported apart. Details, sources and the failures: [eval/README.md](eval/README.md).

| Metric (2026-10-29) | Result |
| --- | --- |
| Damage type right, from the photo alone | 38/39 (97 %) |
| Photos with no damage rejected | 8/9 (89 %) |
| SAM found the damage | 30/39 (77 %) |
| Dedup: same damage joined / different damage kept apart | 3/4 · 5/5 |
| Before/after check right | 12/12 (real pairs 7/7) |
| Marked repaired when it was not | 0 |
| Average model cost per report | $0.011 |

The first run did mark one damage as repaired from a photo of the same avenue taken from another angle. Since then a repair needs more certainty from Muse Spark (0.85) than a reopening (0.7). With 55 photos these are indicative numbers, not a benchmark.
