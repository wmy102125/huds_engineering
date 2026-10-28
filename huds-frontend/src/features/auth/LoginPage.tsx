import { useState } from 'react'
import type { SubmitEventHandler } from 'react'
import { login } from '../../api/auth'
import { ApiError } from '../../api/http'
import type { CurrentUser } from '../../api/types'
import logo from '../../assets/img/logo.svg'

interface Props {
  onLoggedIn: (user: CurrentUser) => void
}

export function LoginPage({ onLoggedIn }: Props) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      onLoggedIn(await login(email.trim(), password))
    } catch (caught) {
      if (caught instanceof ApiError && caught.isNetworkError) {
        setError('The server cannot be reached. Check the connection and try again.')
      } else {
        setError(caught instanceof ApiError ? caught.message : 'Login failed.')
      }
      setBusy(false)
    }
  }

  return (
    <main className="login">
      <img src={logo} width={72} height={72} alt="" />
      <h1>HADS</h1>
      <form className="card" onSubmit={handleSubmit}>
        <label>
          Email
          <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="username" />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
          />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" className="primary" disabled={busy || email === '' || password === ''}>
          {busy ? 'Signing in...' : 'Sign in'}
        </button>
      </form>
    </main>
  )
}
