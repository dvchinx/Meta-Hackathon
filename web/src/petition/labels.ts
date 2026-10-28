import type { IdType } from '../api/petitions'

export const ID_TYPE_LABELS: Record<IdType, string> = {
  CC: 'Cédula de ciudadanía',
  CE: 'Cédula de extranjería',
  TI: 'Tarjeta de identidad',
  PA: 'Pasaporte',
  PEP: 'Permiso especial de permanencia',
  PPT: 'Permiso por protección temporal',
}
