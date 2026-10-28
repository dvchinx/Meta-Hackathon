// One icon per damage type, in src/assets/icons/damage. Vite turns each import into a URL.
import blockedDrain from '../assets/icons/damage/blocked-drain.svg'
import damagedPole from '../assets/icons/damage/damaged-pole.svg'
import damagedSidewalk from '../assets/icons/damage/damaged-sidewalk.svg'
import garbageDump from '../assets/icons/damage/garbage-dump.svg'
import openManhole from '../assets/icons/damage/open-manhole.svg'
import other from '../assets/icons/damage/other.svg'
import pothole from '../assets/icons/damage/pothole.svg'
import riskyTree from '../assets/icons/damage/risky-tree.svg'
import streetFurniture from '../assets/icons/damage/street-furniture.svg'
import streetlightOut from '../assets/icons/damage/streetlight-out.svg'
import waterLeak from '../assets/icons/damage/water-leak.svg'
import type { DamageType } from './types'

export const DAMAGE_ICONS: Record<DamageType, string> = {
  POTHOLE: pothole,
  OPEN_MANHOLE: openManhole,
  DAMAGED_POLE: damagedPole,
  RISKY_TREE: riskyTree,
  STREETLIGHT_OUT: streetlightOut,
  WATER_LEAK: waterLeak,
  BLOCKED_DRAIN: blockedDrain,
  GARBAGE_DUMP: garbageDump,
  DAMAGED_SIDEWALK: damagedSidewalk,
  DAMAGED_STREET_FURNITURE: streetFurniture,
  OTHER: other,
}

/** File name (without .svg) of each type's icon; the map rasterizes them as marker glyphs. */
export const DAMAGE_ICON_FILES: Record<DamageType, string> = {
  POTHOLE: 'pothole',
  OPEN_MANHOLE: 'open-manhole',
  DAMAGED_POLE: 'damaged-pole',
  RISKY_TREE: 'risky-tree',
  STREETLIGHT_OUT: 'streetlight-out',
  WATER_LEAK: 'water-leak',
  BLOCKED_DRAIN: 'blocked-drain',
  GARBAGE_DUMP: 'garbage-dump',
  DAMAGED_SIDEWALK: 'damaged-sidewalk',
  DAMAGED_STREET_FURNITURE: 'street-furniture',
  OTHER: 'other',
}
