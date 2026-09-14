import axios from 'axios'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('cni_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('cni_token')
      localStorage.removeItem('cni_user')
      if (!location.pathname.startsWith('/login')) location.href = '/login'
    }
    return Promise.reject(error)
  }
)
