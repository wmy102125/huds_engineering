import { useCallback, useEffect, useState } from 'react'
import type { OutboxItem } from '../../db/db'
import { describeChange, discardRejected, listRejected } from '../../sync/rejected'
import type { SyncStatus } from '../../sync/useSyncEngine'

interface Props {
  sync: SyncStatus
}

/** Lists the changes the server refused, on every page of the "My" area. */
export function RejectedChanges({ sync }: Props) {
  const [items, setItems] = useState<OutboxItem[]>([])

  const reload = useCallback(async () => {
    setItems(await listRejected())
  }, [])

  useEffect(() => {
    // Started from a timer, not from the effect body, so the first render is not repeated at once.
    const timer = window.setTimeout(() => void reload(), 0)
    return () => window.clearTimeout(timer)
  }, [reload, sync.tick])

  const handleDiscard = async (item: OutboxItem) => {
    await discardRejected(item)
    await reload()
    void sync.requestSync()
  }

  if (items.length === 0) {
    return null
  }
  return (
    <div className="card attention">
      <h3>Needs attention</h3>
      {items.map((item) => (
        <div key={item.seq} className="row between">
          <span>
            {describeChange(item)}: {item.error?.message ?? item.error?.code ?? 'refused by the server'}
          </span>
          <button type="button" onClick={() => void handleDiscard(item)}>
            Discard change
          </button>
        </div>
      ))}
    </div>
  )
}
