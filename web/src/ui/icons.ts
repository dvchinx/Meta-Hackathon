// Icon files live in src/assets/icons; Vite turns each import into a URL.
import alertTriangle from '../assets/icons/alert-triangle.svg'
import arrowLeft from '../assets/icons/arrow-left.svg'
import camera from '../assets/icons/camera.svg'
import checkCircle from '../assets/icons/check-circle.svg'
import eye from '../assets/icons/eye.svg'
import info from '../assets/icons/info.svg'
import layers from '../assets/icons/layers.svg'
import list from '../assets/icons/list.svg'
import lock from '../assets/icons/lock.svg'
import logOut from '../assets/icons/log-out.svg'
import mail from '../assets/icons/mail.svg'
import locate from '../assets/icons/locate.svg'
import mapPin from '../assets/icons/map-pin.svg'
import map from '../assets/icons/map.svg'
import plus from '../assets/icons/plus.svg'
import refresh from '../assets/icons/refresh.svg'
import shield from '../assets/icons/shield.svg'
import user from '../assets/icons/user.svg'

export const icons = {
  alertTriangle,
  arrowLeft,
  camera,
  checkCircle,
  eye,
  info,
  layers,
  list,
  lock,
  logOut,
  mail,
  locate,
  mapPin,
  map,
  plus,
  refresh,
  shield,
  user,
} as const

export type IconName = keyof typeof icons
