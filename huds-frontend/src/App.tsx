import { useCallback, useEffect, useState } from 'react'
import { fetchMe } from './api/auth'
import { ApiError, setUnauthenticatedHandler } from './api/http'
import type { CurrentUser } from './api/types'
import { closeDatabase, openDatabase } from './db/db'
import { ApplianceDetailPage } from './features/appliances/ApplianceDetailPage'
import { ModelDetailPage } from './features/appliances/ModelDetailPage'
import { ModelsPage } from './features/appliances/ModelsPage'
import { LoginPage } from './features/auth/LoginPage'
import { CustomerDetailPage } from './features/customers/CustomerDetailPage'
import { CustomersPage } from './features/customers/CustomersPage'
import { ComingSoonPage } from './features/my/ComingSoonPage'
import { MyLayout } from './features/my/MyLayout'
import { HomePage } from './features/public/HomePage'
import { Redirect, redirect, useRoute } from './routes/useRoute'
import { useSyncEngine } from './sync/useSyncEngine'

type Session =
  | { state: 'loading' }
  | { state: 'anonymous' }
  | { state: 'unreachable' }
  | { state: 'ready'; user: CurrentUser }

function App() {
  const [session, setSession] = useState<Session>({ state: 'loading' })
  const route = useRoute()

  const start = useCallback((user: CurrentUser) => {
    openDatabase(user.userId)
    setSession({ state: 'ready', user })
  }, [])

  const restoreSession = useCallback(async () => {
    try {
      start(await fetchMe())
    } catch (error) {
      if (error instanceof ApiError && error.isNetworkError) {
        setSession({ state: 'unreachable' })
      } else {
        setSession({ state: 'anonymous' })
      }
    }
  }, [start])

  const handleLoggedOut = useCallback(() => {
    closeDatabase()
    setSession({ state: 'anonymous' })
    redirect('/')
  }, [])

  useEffect(() => {
    setUnauthenticatedHandler(() => {
      closeDatabase()
      setSession({ state: 'anonymous' })
    })
    // Started from a timer, not from the effect body, so the first render is not repeated at once.
    const timer = window.setTimeout(() => void restoreSession(), 0)
    return () => {
      window.clearTimeout(timer)
      setUnauthenticatedHandler(null)
    }
  }, [restoreSession])

  if (session.state === 'ready') {
    return <SignedIn user={session.user} route={route} onLoggedOut={handleLoggedOut} />
  }

  // The public pages work without a session.
  if (route === '/login') {
    return (
      <div className="app">
        <LoginPage
          onLoggedIn={(user) => {
            start(user)
            redirect('/my/customers')
          }}
        />
      </div>
    )
  }
  if (route.startsWith('/my')) {
    if (session.state === 'loading') {
      return <p className="app center muted">Loading...</p>
    }
    if (session.state === 'unreachable') {
      return (
        <main className="app center">
          <p>The server cannot be reached.</p>
          <button type="button" onClick={() => void restoreSession()}>
            Try again
          </button>
        </main>
      )
    }
    return <Redirect to="/login" />
  }
  return <HomePage signedIn={false} />
}

interface SignedInProps {
  user: CurrentUser
  route: string
  onLoggedOut: () => void
}

/** Everything a logged-in user sees. The sync engine runs as long as this is on screen. */
function SignedIn({ user, route, onLoggedOut }: SignedInProps) {
  const sync = useSyncEngine(user.userId)

  if (route === '/login') {
    return <Redirect to="/my/customers" />
  }
  if (!route.startsWith('/my')) {
    return <HomePage signedIn />
  }

  // "/my/customers/<id>" -> ['my', 'customers', '<id>']
  const parts = route.split('/').filter(Boolean)
  let page
  if (parts[1] === 'appliances') {
    if (parts[2] === 'models' && parts[3]) {
      page = <ModelDetailPage key={parts[3]} applianceModelId={parts[3]} sync={sync} />
    } else if (parts[2] === 'items' && parts[3]) {
      page = <ApplianceDetailPage key={parts[3]} applianceId={parts[3]} sync={sync} />
    } else {
      page = <ModelsPage sync={sync} />
    }
  } else if (parts[1] === 'cases') {
    page = <ComingSoonPage title="Cases" note="Cases are not built on the server yet." />
  } else if (parts[1] === 'customers' && parts[2]) {
    page = <CustomerDetailPage key={parts[2]} customerId={parts[2]} sync={sync} />
  } else {
    page = <CustomersPage sync={sync} />
  }
  return (
    <MyLayout user={user} sync={sync} route={route} onLoggedOut={onLoggedOut}>
      {page}
    </MyLayout>
  )
}

export default App
