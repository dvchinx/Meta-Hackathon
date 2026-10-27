import type { FeatureCollection, Point } from 'geojson'
import type { GeoJSONSource, MapLayerMouseEvent } from 'maplibre-gl'
import { useEffect, useRef } from 'react'
import { DAMAGE_LABELS, STATUS_COLORS, STATUS_LABELS, reportsLabel, timeOpen } from '../issues/labels'
import type { IssueSummary } from '../issues/types'
import { BOGOTA, STYLE_URL, maplibregl } from './maplibre'

function toGeoJson(issues: IssueSummary[]): FeatureCollection<Point> {
  return {
    type: 'FeatureCollection',
    features: issues.map((issue) => ({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [issue.longitude, issue.latitude] },
      properties: { id: issue.id, status: issue.status, reportCount: issue.reportCount },
    })),
  }
}

function element<K extends keyof HTMLElementTagNameMap>(tag: K, className: string, text?: string) {
  const el = document.createElement(tag)
  el.className = className
  // textContent, never innerHTML: data comes from citizens.
  if (text) el.textContent = text
  return el
}

function popupContent(issue: IssueSummary): HTMLElement {
  const root = element('div', 'issue-popup')
  if (issue.photoUrl) {
    const img = element('img', '')
    img.src = issue.photoUrl
    img.alt = `Foto del daño: ${DAMAGE_LABELS[issue.damageType]}`
    root.append(img)
  }
  const body = element('div', 'issue-popup-body')
  body.append(element('div', 'issue-popup-title', DAMAGE_LABELS[issue.damageType]))

  const status = element('div', 'issue-popup-status')
  const dot = element('span', 'dot')
  dot.style.background = STATUS_COLORS[issue.status]
  status.append(dot, document.createTextNode(STATUS_LABELS[issue.status]))
  body.append(status)

  body.append(element('div', 'issue-popup-meta', `${reportsLabel(issue.reportCount)} · ${timeOpen(issue.openedAt)}`))
  if (issue.agency) body.append(element('div', 'issue-popup-meta', `Responsable: ${issue.agency}`))
  root.append(body)
  return root
}

const statusColor = [
  'match',
  ['get', 'status'],
  ...Object.entries(STATUS_COLORS).flat(),
  '#666666',
] as unknown as maplibregl.ExpressionSpecification // built from a Record: TS cannot see the tuple shape

export function IssueMap({ issues }: { issues: IssueSummary[] }) {
  const container = useRef<HTMLDivElement>(null)
  const map = useRef<maplibregl.Map | null>(null)
  const issuesById = useRef(new Map<string, IssueSummary>())
  // Latest issues, read by the 'load' handler: data may arrive before or after the map loads.
  const latestIssues = useRef(issues)

  useEffect(() => {
    if (!container.current) return
    const instance = new maplibregl.Map({ container: container.current, style: STYLE_URL, center: BOGOTA, zoom: 11 })
    instance.addControl(new maplibregl.NavigationControl(), 'top-right')
    instance.addControl(new maplibregl.GeolocateControl({}), 'top-right')

    instance.on('load', () => {
      instance.addSource('issues', { type: 'geojson', data: toGeoJson(latestIssues.current) })
      instance.addLayer({
        id: 'issues',
        type: 'circle',
        source: 'issues',
        paint: {
          'circle-color': statusColor,
          'circle-radius': ['interpolate', ['linear'], ['get', 'reportCount'], 1, 7, 10, 16],
          'circle-stroke-width': 2,
          'circle-stroke-color': '#ffffff',
        },
      })
      instance.on('click', 'issues', (event: MapLayerMouseEvent) => {
        const id = event.features?.[0]?.properties?.id as string | undefined
        const issue = id ? issuesById.current.get(id) : undefined
        if (!issue) return
        new maplibregl.Popup({ maxWidth: '260px', offset: 12 })
          .setLngLat([issue.longitude, issue.latitude])
          .setDOMContent(popupContent(issue))
          .addTo(instance)
      })
      instance.on('mouseenter', 'issues', () => (instance.getCanvas().style.cursor = 'pointer'))
      instance.on('mouseleave', 'issues', () => (instance.getCanvas().style.cursor = ''))
    })

    map.current = instance
    return () => {
      instance.remove()
      map.current = null
    }
  }, [])

  useEffect(() => {
    latestIssues.current = issues
    issuesById.current = new Map(issues.map((issue) => [issue.id, issue]))
    // Before 'load' the source does not exist yet; the load handler picks up latestIssues instead.
    const source = map.current?.getSource('issues') as GeoJSONSource | undefined
    source?.setData(toGeoJson(issues))
  }, [issues])

  return <div ref={container} className="issue-map" role="region" aria-label="Mapa de daños reportados" />
}
