import axios from 'axios'
import { decryptApiBody, encryptApiBody } from './apiCrypto'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15000
})

api.interceptors.request.use(async config => {
  const token = localStorage.getItem('cni_token')
  if (token) config.headers.Authorization = `Bearer ${token}`

  const contentType = String(config.headers['Content-Type'] || config.headers['content-type'] || '')
  const requestUrl = String(config.url || '')
  const isAuthRequest = /\/auth(?:\/|$)/i.test(requestUrl)
  const isJson = contentType.toLowerCase().includes('application/json') ||
    (config.data && typeof config.data === 'object' && !(config.data instanceof FormData) && !(config.data instanceof Blob))

  // Authentication/bootstrap endpoints intentionally remain plain JSON. They are
  // still protected by the application's transport/authentication controls, while
  // avoiding a circular dependency where the login request must already know the
  // application-layer encryption secret. Operational API traffic remains encrypted.
  if (!isAuthRequest && isJson && config.data !== undefined && config.data !== null) {
    const encrypted = await encryptApiBody(config.data)
    if (encrypted) {
      config.data = encrypted
      config.headers['Content-Type'] = 'text/plain;charset=UTF-8'
      config.headers['X-Hososhi-Encrypted'] = 'true'
    }
  }
  return config
})

api.interceptors.response.use(
  async response => {
    if (response.headers['x-hososhi-encrypted'] === 'true' ||
        (typeof response.data === 'string' && response.data.startsWith('HOSOSHI1.'))) {
      response.data = await decryptApiBody(response.data)
    }
    return response
  },
  async error => {
    if (error.response?.data) {
      try {
        if (error.response.headers?.['x-hososhi-encrypted'] === 'true' ||
            (typeof error.response.data === 'string' && error.response.data.startsWith('HOSOSHI1.'))) {
          error.response.data = await decryptApiBody(error.response.data)
        }
      } catch {
        // Preserve the original Axios error when an error response cannot be decrypted.
      }
    }
    if (error.response?.status === 401) {
      localStorage.removeItem('cni_token')
      localStorage.removeItem('cni_user')
      if (!location.pathname.startsWith('/login')) location.href = '/login'
    }
    return Promise.reject(error)
  }
)
