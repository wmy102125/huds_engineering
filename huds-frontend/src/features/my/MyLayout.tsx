import type { ReactNode } from 'react'
import logo from '../../assets/img/logo.svg'
import type { CurrentUser } from '../../api/types'
import { logout } from '../../api/auth'
import { navigate } from '../../routes/useRoute'
import { countWaiting } from '../../sync/outbox'
import type { SyncStatus } from '../../sync/useSyncEngine'
import { RejectedChanges } from './RejectedChanges'

interface Props {
  user: CurrentUser
  sync: SyncStatus
  route: string
  onLoggedOut: () => void
  children: ReactNode
}

const MENU = [
  { path: '/my/customers', label: 'Customers' },
  { path: '/my/appliances', label: 'Appliances' },
  { path: '/my/cases', label: 'Cases' },
]

/** The frame of the "My" area: header with sync status, the menu, and the page. */
export function MyLayout({ user, sync, route, onLoggedOut, children }: Props) {
  const handleLogout = async () => {
    const waiting = await countWaiting()
    if (
      waiting > 0 &&
      !window.confirm(
        `${waiting} change(s) are not synced yet. They stay on this device and are sent after you sign in again. Sign out now?`,
      )
    ) {
      return
    }
    try {
      await logout()
    } catch {
      // The session is dropped on this device in any case.
    }
    onLoggedOut()
  }

  return (
    <div className="app shell">
      <aside className="sidebar">
        <a className="row brand" href="#/">
          <img src={logo} width={32} height={32} alt="" />
          <strong>HADS</strong>
        </a>
        <nav className="menu" aria-label="My area">
          {MENU.map((item) => (
            <a
              key={item.path}
              href={`#${item.path}`}
              className={route === item.path || route.startsWith(`${item.path}/`) ? 'active' : undefined}
              onClick={(event) => {
                event.preventDefault()
                navigate(item.path)
              }}
            >
              {item.label}
            </a>
          ))}
        </nav>
      </aside>
      <div className="main">
        <header className="topbar">
          <SyncIndicator sync={sync} />
          <div className="row">
            <span className="muted hide-small">{user.userName}</span>
            <button type="button" onClick={() => void handleLogout()}>
              Sign out
            </button>
          </div>
        </header>
        <main className="content">
          <RejectedChanges sync={sync} />
          {children}
        </main>
      </div>
    </div>
  )
}

function SyncIndicator({ sync }: { sync: SyncStatus }) {
  let text = 'All changes synced'
  let kind = 'ok'
  if (!sync.online) {
    text = sync.waiting > 0 ? `Offline - ${sync.waiting} waiting` : 'Offline'
    kind = 'bad'
  } else if (sync.syncing) {
    text = 'Syncing...'
    kind = 'wait'
  } else if (sync.message) {
    text = sync.message
    kind = 'bad'
  } else if (sync.waiting > 0) {
    text = `${sync.waiting} waiting to sync`
    kind = 'wait'
  }
  return <span className={`badge ${kind}`}>{text}</span>
}
