import { STORAGE_KEYS } from '../constants'

export function decodeJwt(token) {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const payload = parts[1]
    const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    return JSON.parse(decoded)
  } catch {
    return null
  }
}

export function getUserIdFromToken(token) {
  const payload = token ? decodeJwt(token) : null
  if (!payload) return null
  return payload.sub || payload.userId || null
}

export function getUsernameFromToken(token) {
  const payload = token ? decodeJwt(token) : null
  return payload ? payload.username || null : null
}

export function getCurrentUserId() {
  const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
  return getUserIdFromToken(token)
}

export function getUserRole() {
  const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
  if (!token) return null
  const payload = decodeJwt(token)
  return payload ? payload.role : null
}

export function isAdmin() {
  return getUserRole() === 'ADMIN'
}

export function isTokenValid(token) {
  const payload = token ? decodeJwt(token) : null
  if (!payload || !payload.exp) return false
  return Date.now() < payload.exp * 1000
}
