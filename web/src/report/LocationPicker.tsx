import { useEffect, useRef, useState } from 'react'
import { BOGOTA, COLOMBIA_BOUNDS, STYLE_URL, maplibregl } from '../map/maplibre'
import { Icon } from '../ui/Icon'

export interface PickedLocation {
  latitude: number
  longitude: number
}

const PIN_COLOR = '#b42318'

/** Small map: tap to drop the pin, drag it to adjust, or use the device location. */
export function LocationPicker({
  value,
  onChange,
}: {
  value: PickedLocation | null
  onChange: (l: PickedLocation) => void
}) {
  const container = useRef<HTMLDivElement>(null)
  const map = useRef<maplibregl.Map | null>(null)
  const marker = useRef<maplibregl.Marker | null>(null)
  const onChangeRef = useRef(onChange)
  const [locating, setLocating] = useState(false)
  const [geoError, setGeoError] = useState<string | null>(null)

  useEffect(() => {
    onChangeRef.current = onChange
  }, [onChange])

  useEffect(() => {
    if (!container.current) return
    const instance = new maplibregl.Map({
      container: container.current,
      style: STYLE_URL,
      center: BOGOTA,
      zoom: 11,
      maxBounds: COLOMBIA_BOUNDS,
      attributionControl: { compact: true },
    })
    instance.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right')
    instance.on('click', (e) => onChangeRef.current({ latitude: e.lngLat.lat, longitude: e.lngLat.lng }))
    map.current = instance
    return () => {
      instance.remove()
      map.current = null
      marker.current = null
    }
  }, [])

  useEffect(() => {
    const instance = map.current
    if (!instance || !value) return
    const lngLat: [number, number] = [value.longitude, value.latitude]
    if (!marker.current) {
      marker.current = new maplibregl.Marker({ color: PIN_COLOR, draggable: true }).setLngLat(lngLat).addTo(instance)
      marker.current.on('dragend', () => {
        const p = marker.current!.getLngLat()
        onChangeRef.current({ latitude: p.lat, longitude: p.lng })
      })
    } else {
      marker.current.setLngLat(lngLat)
    }
  }, [value])

  function useDeviceLocation() {
    if (!('geolocation' in navigator)) {
      setGeoError('Tu navegador no permite obtener la ubicación. Marca el punto en el mapa.')
      return
    }
    setLocating(true)
    setGeoError(null)
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false)
        const picked = { latitude: position.coords.latitude, longitude: position.coords.longitude }
        onChangeRef.current(picked)
        map.current?.flyTo({ center: [picked.longitude, picked.latitude], zoom: 17 })
      },
      () => {
        setLocating(false)
        setGeoError('No pudimos obtener tu ubicación. Marca el punto en el mapa.')
      },
      { enableHighAccuracy: true, timeout: 15000 },
    )
  }

  return (
    <div>
      <div ref={container} className="location-map" role="region" aria-label="Mapa para marcar la ubicación del daño" />
      <div className="location-bar">
        <button type="button" className="button button-secondary" onClick={useDeviceLocation} disabled={locating}>
          <Icon name="locate" size={18} />
          {locating ? 'Buscando ubicación…' : 'Usar mi ubicación'}
        </button>
        <span className="location-readout" aria-live="polite">
          {value ? (
            <>
              <Icon name="mapPin" size={16} />
              {value.latitude.toFixed(5)}, {value.longitude.toFixed(5)} · arrastra el pin para ajustar
            </>
          ) : (
            'Toca el mapa en el punto exacto del daño.'
          )}
        </span>
      </div>
      {geoError && <p className="form-error">{geoError}</p>}
    </div>
  )
}
