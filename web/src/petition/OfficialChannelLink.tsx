import type { OfficialChannel } from '../issues/types'
import { Icon } from '../ui/Icon'

/** Our filing is simulated: always point to where the citizen can report for real. */
export function OfficialChannelLink({ channel }: { channel: OfficialChannel }) {
  return (
    <p className="official-channel">
      <Icon name="info" size={16} />
      <span>
        ¿Quieres radicar de verdad?{' '}
        <a href={channel.url} target="_blank" rel="noreferrer">
          Usa el canal oficial
        </a>
        {channel.phone && <> o llama a la {channel.phone}</>}.
      </span>
    </p>
  )
}
