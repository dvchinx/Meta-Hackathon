import * as maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
// MapLibre 6 loads its worker by URL; let Vite bundle it (with its shared chunk) as an ES worker.
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'

maplibregl.setWorkerUrl(workerUrl)

// OpenFreeMap: free OpenStreetMap vector tiles, no API key.
export const STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty'
export const BOGOTA: [number, number] = [-74.0817, 4.6097]

export { maplibregl }
