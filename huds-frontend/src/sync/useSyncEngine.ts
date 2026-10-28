import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/http'
import { countWaiting } from './outbox'
import { pullAll } from './pull'
import { syncNow } from './syncEngine'

const SYNC_INTERVAL_MS = 30_000

export interface SyncStatus {
  /** Browser says it has a network connection (not always right). */
  online: boolean
  syncing: boolean
  /** Changes made on this device that the server does not have yet. */
  waiting: number
  /** Number that grows after every sync, so pages know when to read the local data again. */
  tick: number
  message: string | null
  /** Starts a sync now (does nothing if one is already running). */
  requestSync: () => Promise<void>
}

/** Keeps the outbox and the server in step while the user is logged in. */
export function useSyncEngine(userId: string): SyncStatus {
  const [online, setOnline] = useState<boolean>(navigator.onLine)
  const [syncing, setSyncing] = useState(false)
  const [waiting, setWaiting] = useState(0)
  const [tick, setTick] = useState(0)
  const [message, setMessage] = useState<string | null>(null)

  const requestSync = useCallback(async () => {
    setSyncing(true)
    try {
      const outcome = await syncNow(userId)
      await pullAll()
      setOnline(true)
      setMessage(outcome.rejected > 0 ? 'Some changes were refused by the server.' : null)
    } catch (error) {
      if (error instanceof ApiError && error.isNetworkError) {
        setOnline(false)
        setMessage(null)
      } else {
        setMessage(error instanceof ApiError ? error.message : 'Sync failed.')
      }
    } finally {
      setWaiting(await countWaiting())
      setSyncing(false)
      setTick((value) => value + 1)
    }
  }, [userId])

  useEffect(() => {
    const handleOnline = () => {
      setOnline(true)
      void requestSync()
    }
    const handleOffline = () => setOnline(false)
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)

    const firstRun = window.setTimeout(() => void requestSync(), 0)
    const timer = window.setInterval(() => void requestSync(), SYNC_INTERVAL_MS)

    return () => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
      window.clearTimeout(firstRun)
      window.clearInterval(timer)
    }
  }, [requestSync])

  return { online, syncing, waiting, tick, message, requestSync }
}
