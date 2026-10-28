import { useCallback, useEffect, useState } from 'react'

/**
 * Reads data from the local database for a page, and reads it again after every sync.
 * `load` has to keep the same identity between renders (a function outside the component, or useCallback),
 * otherwise the data would be read again on every render.
 * `data` is undefined until the first read has finished.
 */
export function useLocalQuery<T>(load: () => Promise<T>, tick: number): { data: T | undefined; reload: () => Promise<void> } {
  const [data, setData] = useState<T | undefined>(undefined)

  const reload = useCallback(async () => {
    setData(await load())
  }, [load])

  useEffect(() => {
    // Started from a timer, not from the effect body, so the first render is not repeated at once.
    const timer = window.setTimeout(() => void reload(), 0)
    return () => window.clearTimeout(timer)
  }, [reload, tick])

  return { data, reload }
}
