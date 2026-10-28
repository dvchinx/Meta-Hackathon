import type { DamageType } from '../issues/types'
import { PRIVACY_POLICY_VERSION } from '../privacy/policy'
import { parseJson, postJson } from './http'

export type IdType = 'CC' | 'CE' | 'TI' | 'PA' | 'PEP' | 'PPT'

/** What the agencies ask of a petitioner. Stored encrypted; never public. */
export interface FilerProfile {
  idType: IdType
  idNumber: string
  firstNames: string
  lastNames: string
  mobilePhone: string
  /** Optional postal address. */
  address: string | null
}

export interface PetitionReceipt {
  petitionId: string
  issueId: string
  radicado: string
  filedAt: string
  dueDate: string
  agency: string
}

/** The printable petition. Only its filer can read it. */
export interface PetitionDocument {
  petitionId: string
  radicado: string
  filedAt: string
  dueDate: string
  agency: string
  petitioner: {
    idType: IdType
    idNumber: string
    fullName: string
    mobilePhone: string
    address: string | null
    email: string
  }
  subject: string
  issueId: string
  damageType: DamageType
  latitude: number
  longitude: number
  locality: string | null
  reportCount: number
  openedAt: string
  photoUrl: string | null
}

const JSON_HEADERS = { Accept: 'application/json' }

export async function fetchFilerProfile(signal?: AbortSignal): Promise<FilerProfile | null> {
  const body = await parseJson<{ profile: FilerProfile | null }>(
    await fetch('/api/me/filer-profile', { signal, headers: JSON_HEADERS }),
  )
  return body.profile
}

/** Saving means accepting the current data policy: its version goes along so the backend can record it. */
export async function saveFilerProfile(profile: FilerProfile): Promise<FilerProfile> {
  const response = await fetch('/api/me/filer-profile', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...JSON_HEADERS },
    body: JSON.stringify({ profile, acceptedPolicyVersion: PRIVACY_POLICY_VERSION }),
  })
  return (await parseJson<{ profile: FilerProfile }>(response)).profile
}

export async function deleteFilerProfile(): Promise<void> {
  await parseJson<void>(await fetch('/api/me/filer-profile', { method: 'DELETE' }))
}

/** Simulated filing. The citizen authorized sharing their data with the agency under the current policy. */
export async function filePetition(issueId: string): Promise<PetitionReceipt> {
  return parseJson<PetitionReceipt>(
    await postJson(`/api/issues/${encodeURIComponent(issueId)}/petitions`, {
      acceptedPolicyVersion: PRIVACY_POLICY_VERSION,
    }),
  )
}

export async function fetchPetition(id: string, signal?: AbortSignal): Promise<PetitionDocument> {
  return parseJson<PetitionDocument>(
    await fetch(`/api/petitions/${encodeURIComponent(id)}`, { signal, headers: JSON_HEADERS }),
  )
}
