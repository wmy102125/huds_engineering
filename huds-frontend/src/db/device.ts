const DEVICE_ID_KEY = 'huds.deviceId'

/**
 * Returns the id of this browser on this computer or phone. It is created once and kept,
 * and it is sent at login so the server can tell which device made a change.
 */
export function getDeviceId(): string {
  try {
    const stored = localStorage.getItem(DEVICE_ID_KEY)
    if (stored) {
      return stored
    }
    const created = crypto.randomUUID()
    localStorage.setItem(DEVICE_ID_KEY, created)
    return created
  } catch {
    // Storage is blocked (private window). Use an id that lasts until the page is closed.
    return crypto.randomUUID()
  }
}
