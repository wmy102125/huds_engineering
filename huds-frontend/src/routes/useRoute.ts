import { useEffect, useSyncExternalStore } from 'react'

// The app uses "hash" addresses such as  http://localhost:5173/#/my/customers .
// They work on any static host without server settings, and the part after # never reaches the server.

function subscribe(onChange: () => void): () => void {
  window.addEventListener('hashchange', onChange)
  return () => window.removeEventListener('hashchange', onChange)
}

function currentPath(): string {
  const hash = window.location.hash
  return hash.startsWith('#/') ? hash.slice(1) : '/'
}

/** The current page address, for example "/", "/login" or "/my/customers". The component renders again when it changes. */
export function useRoute(): string {
  return useSyncExternalStore(subscribe, currentPath)
}

/** Goes to another page and keeps the old one in the browser history. */
export function navigate(path: string): void {
  window.location.hash = path
}

/** Goes to another page without adding a history entry (the Back button skips the old one). */
export function redirect(path: string): void {
  window.location.replace(`#${path}`)
}

/** Renders nothing and moves to another page (used where a page is not allowed for the current user). */
export function Redirect({ to }: { to: string }): null {
  useEffect(() => {
    redirect(to)
  }, [to])
  return null
}
