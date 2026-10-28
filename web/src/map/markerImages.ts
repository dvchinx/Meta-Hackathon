import { DAMAGE_ICON_FILES } from '../issues/damageIcons'
import type { DamageType } from '../issues/types'
import type { maplibregl } from './maplibre'

// Raw SVG text of every damage icon, to rasterize as white marker glyphs.
const SOURCES = import.meta.glob<string>('../assets/icons/damage/*.svg', { query: '?raw', import: 'default', eager: true })

const PIXELS = 48 // drawn at 2x for sharp markers on high-density screens

export function markerImageId(type: DamageType): string {
  return `damage-${DAMAGE_ICON_FILES[type]}`
}

async function rasterize(svg: string): Promise<ImageData> {
  const white = svg.replace(/stroke="#000"/g, 'stroke="#fff"').replace('stroke-width="1.75"', 'stroke-width="2.25"')
  const image = new Image(PIXELS, PIXELS)
  image.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(white)}`
  await image.decode()
  const canvas = document.createElement('canvas')
  canvas.width = PIXELS
  canvas.height = PIXELS
  const context = canvas.getContext('2d')!
  context.drawImage(image, 0, 0, PIXELS, PIXELS)
  return context.getImageData(0, 0, PIXELS, PIXELS)
}

/** Registers one glyph per damage type. A glyph that fails to load only leaves its markers without icon. */
export async function addMarkerImages(map: maplibregl.Map): Promise<void> {
  await Promise.all(
    (Object.keys(DAMAGE_ICON_FILES) as DamageType[]).map(async (type) => {
      const source = SOURCES[`../assets/icons/damage/${DAMAGE_ICON_FILES[type]}.svg`]
      if (!source || map.hasImage(markerImageId(type))) return
      try {
        map.addImage(markerImageId(type), await rasterize(source), { pixelRatio: 2 })
      } catch (e) {
        console.warn(`No se pudo cargar el ícono ${type}`, e)
      }
    }),
  )
}
