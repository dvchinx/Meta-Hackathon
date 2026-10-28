import type { FeatureCollection, Point } from 'geojson'
import type { GeoJSONSource, MapLayerMouseEvent } from 'maplibre-gl'
import { useEffect, useRef } from 'react'
import { DAMAGE_ICON_FILES } from '../issues/damageIcons'
import { DAMAGE_LABELS, STATUS_COLORS, STATUS_LABELS, canFile, deadlineLabel, reportsLabel, timeOpen } from '../issues/labels'
import type { Bounds } from '../issues/filters'
import type { IssueSummary } from '../issues/types'
import { href } from '../routing'
import { icons } from '../ui/icons'
import { BOGOTA, COLOMBIA_BOUNDS, STYLE_URL, maplibregl } from './maplibre'
import { addMarkerImages } from './markerImages'

/** Resolves with the new count, or null when nothing changed (not signed in, error). */
export type EndorseHandler = (issue: IssueSummary) => Promise<{ counted: boolean; reportCount: number } | null>

/** Ask the map to fly to an Issue and open its popup. The nonce lets the same Issue be focused twice. */
export interface MapFocus {
  id: string
  nonce: number
}

function toGeoJson(issues: IssueSummary[]): FeatureCollection<Point> {
  return {
    type: 'FeatureCollection',
    features: issues.map((issue) => ({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [issue.longitude, issue.latitude] },
      properties: {
        id: issue.id,
        status: issue.status,
        reportCount: issue.reportCount,
        icon: `damage-${DAMAGE_ICON_FILES[issue.damageType]}`,
      },
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

function iconElement(name: keyof typeof icons): HTMLSpanElement {
  const icon = element('span', 'icon')
  icon.style.width = '16px'
  icon.style.height = '16px'
  icon.style.maskImage = `url("${icons[name]}")`
  icon.style.webkitMaskImage = `url("${icons[name]}")`
  icon.setAttribute('aria-hidden', 'true')
  return icon
}

function popupContent(issue: IssueSummary, onEndorse: EndorseHandler): HTMLElement {
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

  const meta = element('div', 'issue-popup-meta', `${reportsLabel(issue.reportCount)} · ${timeOpen(issue.openedAt)}`)
  body.append(meta)
  if (issue.locality) body.append(element('div', 'issue-popup-meta', `Localidad: ${issue.locality}`))
  if (issue.agency) body.append(element('div', 'issue-popup-meta', `Responsable: ${issue.agency}`))
  if (issue.petition && issue.status === 'FILED') {
    const tone = issue.petition.overdue ? 'issue-popup-deadline is-overdue' : 'issue-popup-deadline'
    body.append(element('div', tone, deadlineLabel(issue.petition)))
  }

  if (issue.status !== 'VERIFIED') {
    const button = element('button', 'button button-secondary issue-popup-action')
    button.type = 'button'
    button.append(iconElement('eye'), document.createTextNode('Yo también lo vi'))
    button.addEventListener('click', () => {
      button.disabled = true
      void onEndorse(issue).then((result) => {
        if (!result) {
          button.disabled = false
          return
        }
        meta.textContent = `${reportsLabel(result.reportCount)} · ${timeOpen(issue.openedAt)}`
        button.classList.add('is-done')
        button.replaceChildren(iconElement('checkCircle'), document.createTextNode(result.counted ? 'Contamos tu reporte' : 'Ya te habíamos contado'))
      })
    })
    body.append(button)
  }
  if (canFile(issue)) {
    const file = element('a', 'button button-primary issue-popup-action')
    file.href = href('/radicar', { issue: issue.id })
    file.append(iconElement('mail'), document.createTextNode('Radicar petición'))
    body.append(file)
  }
  const detail = element('a', 'issue-popup-link', 'Ver detalle y avance')
  detail.href = href('/dano', { id: issue.id })
  body.append(detail)
  root.append(body)
  return root
}

const statusColor = [
  'match',
  ['get', 'status'],
  ...Object.entries(STATUS_COLORS).flat(),
  '#666666',
] as unknown as maplibregl.ExpressionSpecification // built from a Record: TS cannot see the tuple shape

export function IssueMap({
  issues,
  onEndorse,
  focus,
  onViewChange,
}: {
  issues: IssueSummary[]
  onEndorse: EndorseHandler
  focus?: MapFocus | null
  onViewChange?: (bounds: Bounds) => void
}) {
  const container = useRef<HTMLDivElement>(null)
  const map = useRef<maplibregl.Map | null>(null)
  const popup = useRef<maplibregl.Popup | null>(null)
  const issuesById = useRef(new Map<string, IssueSummary>())
  // Latest values, read by map handlers registered once: data may arrive before or after the map loads.
  const latestIssues = useRef(issues)
  const onEndorseRef = useRef(onEndorse)
  const onViewChangeRef = useRef(onViewChange)
  useEffect(() => {
    onEndorseRef.current = onEndorse
    onViewChangeRef.current = onViewChange
  }, [onEndorse, onViewChange])

  useEffect(() => {
    if (!container.current) return
    const instance = new maplibregl.Map({
      container: container.current,
      style: STYLE_URL,
      center: BOGOTA,
      zoom: 11,
      maxBounds: COLOMBIA_BOUNDS,
    })
    instance.addControl(new maplibregl.NavigationControl(), 'top-right')
    instance.addControl(new maplibregl.GeolocateControl({}), 'top-right')

    const reportView = () => {
      const b = instance.getBounds()
      onViewChangeRef.current?.([b.getWest(), b.getSouth(), b.getEast(), b.getNorth()])
    }

    instance.on('load', () => {
      void addMarkerImages(instance)
      instance.addSource('issues', {
        type: 'geojson',
        data: toGeoJson(latestIssues.current),
        cluster: true,
        clusterMaxZoom: 14,
        clusterRadius: 44,
      })
      instance.addLayer({
        id: 'clusters',
        type: 'circle',
        source: 'issues',
        filter: ['has', 'point_count'],
        paint: {
          'circle-color': '#344054',
          'circle-opacity': 0.9,
          'circle-radius': ['step', ['get', 'point_count'], 16, 10, 20, 50, 26],
          'circle-stroke-width': 3,
          'circle-stroke-color': '#ffffff',
        },
      })
      instance.addLayer({
        id: 'cluster-count',
        type: 'symbol',
        source: 'issues',
        filter: ['has', 'point_count'],
        layout: { 'text-field': ['get', 'point_count_abbreviated'], 'text-font': ['Noto Sans Bold'], 'text-size': 13 },
        paint: { 'text-color': '#ffffff' },
      })
      instance.addLayer({
        id: 'issues',
        type: 'circle',
        source: 'issues',
        filter: ['!', ['has', 'point_count']],
        paint: {
          'circle-color': statusColor,
          'circle-radius': ['interpolate', ['linear'], ['get', 'reportCount'], 1, 12, 10, 19],
          'circle-stroke-width': 2,
          'circle-stroke-color': '#ffffff',
        },
      })
      instance.addLayer({
        id: 'issue-icons',
        type: 'symbol',
        source: 'issues',
        filter: ['!', ['has', 'point_count']],
        layout: {
          'icon-image': ['get', 'icon'],
          'icon-size': ['interpolate', ['linear'], ['get', 'reportCount'], 1, 0.68, 10, 1.05],
          'icon-allow-overlap': true,
          'icon-ignore-placement': true,
        },
      })

      const openIssue = (event: MapLayerMouseEvent) => {
        const id = event.features?.[0]?.properties?.id as string | undefined
        const issue = id ? issuesById.current.get(id) : undefined
        if (issue) showPopup(issue)
      }
      instance.on('click', 'issues', openIssue)
      instance.on('click', 'issue-icons', openIssue)
      instance.on('click', 'clusters', (event: MapLayerMouseEvent) => {
        const feature = event.features?.[0]
        const clusterId = feature?.properties?.cluster_id as number | undefined
        if (feature === undefined || clusterId === undefined) return
        const source = instance.getSource('issues') as GeoJSONSource
        void source.getClusterExpansionZoom(clusterId).then((zoom) => {
          instance.easeTo({ center: (feature.geometry as Point).coordinates as [number, number], zoom })
        })
      })
      for (const layer of ['issues', 'issue-icons', 'clusters']) {
        instance.on('mouseenter', layer, () => (instance.getCanvas().style.cursor = 'pointer'))
        instance.on('mouseleave', layer, () => (instance.getCanvas().style.cursor = ''))
      }
      reportView()
    })
    instance.on('moveend', reportView)

    const showPopup = (issue: IssueSummary) => {
      popup.current?.remove()
      popup.current = new maplibregl.Popup({ maxWidth: '260px', offset: 14 })
        .setLngLat([issue.longitude, issue.latitude])
        .setDOMContent(popupContent(issue, (i) => onEndorseRef.current(i)))
        .addTo(instance)
    }
    showPopupRef.current = showPopup

    map.current = instance
    return () => {
      instance.remove()
      map.current = null
    }
  }, [])

  const showPopupRef = useRef<((issue: IssueSummary) => void) | null>(null)

  useEffect(() => {
    latestIssues.current = issues
    issuesById.current = new Map(issues.map((issue) => [issue.id, issue]))
    // Before 'load' the source does not exist yet; the load handler picks up latestIssues instead.
    const source = map.current?.getSource('issues') as GeoJSONSource | undefined
    source?.setData(toGeoJson(issues))
  }, [issues])

  useEffect(() => {
    const instance = map.current
    const issue = focus ? issuesById.current.get(focus.id) : undefined
    if (!instance || !issue) return
    instance.flyTo({ center: [issue.longitude, issue.latitude], zoom: Math.max(instance.getZoom(), 16) })
    showPopupRef.current?.(issue)
  }, [focus])

  return <div ref={container} className="issue-map" role="region" aria-label="Mapa de daños reportados" />
}
