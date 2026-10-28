import type { SyncState } from '../db/db'

/** Shows whether the server has a record in its latest form. */
export function SyncBadge({ state }: { state: SyncState }) {
  if (state === 'SYNCED') {
    return <span className="badge ok">Synced</span>
  }
  if (state === 'PENDING') {
    return <span className="badge wait">Saved on this device</span>
  }
  return <span className="badge bad">Refused</span>
}
