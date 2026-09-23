const PREFIX = 'HOSOSHI1.'
const IV_LENGTH = 12
const KEY_ENV = import.meta.env.VITE_API_ENCRYPTION_KEY as string | undefined

function base64UrlToBytes(value: string): Uint8Array {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((value.length + 3) % 4)
  const raw = atob(normalized)
  const bytes = new Uint8Array(raw.length)
  for (let i = 0; i < raw.length; i += 1) bytes[i] = raw.charCodeAt(i)
  return bytes
}

/**
 * Return a real ArrayBuffer rather than Uint8Array<ArrayBufferLike>.
 * Newer TypeScript DOM typings require WebCrypto BufferSource values
 * backed by ArrayBuffer, while Uint8Array can otherwise be typed as
 * ArrayBufferLike (which includes SharedArrayBuffer).
 */
function bytesToArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  const buffer = new ArrayBuffer(bytes.byteLength)
  new Uint8Array(buffer).set(bytes)
  return buffer
}

function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = ''
  bytes.forEach(byte => { binary += String.fromCharCode(byte) })
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

let cryptoKeyPromise: Promise<CryptoKey | null> | null = null

function getKey(): Promise<CryptoKey | null> {
  if (!KEY_ENV) return Promise.resolve(null)
  if (!cryptoKeyPromise) {
    cryptoKeyPromise = crypto.subtle.importKey(
      'raw',
      bytesToArrayBuffer(base64UrlToBytes(KEY_ENV)),
      { name: 'AES-GCM' },
      false,
      ['encrypt', 'decrypt']
    ).catch(error => {
      cryptoKeyPromise = null
      throw new Error(`HOSOSHI API encryption key is invalid: ${error instanceof Error ? error.message : String(error)}`)
    })
  }
  return cryptoKeyPromise
}

export async function encryptApiBody(value: unknown): Promise<string | null> {
  const key = await getKey()
  if (!key) return null

  const iv = crypto.getRandomValues(new Uint8Array(IV_LENGTH))
  const plaintext = new TextEncoder().encode(JSON.stringify(value))
  const ciphertext = new Uint8Array(await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: bytesToArrayBuffer(iv) },
    key,
    bytesToArrayBuffer(plaintext)
  ))

  return `${PREFIX}${bytesToBase64Url(iv)}.${bytesToBase64Url(ciphertext)}`
}

export async function decryptApiBody(value: unknown): Promise<unknown> {
  if (typeof value !== 'string' || !value.startsWith(PREFIX)) return value

  const key = await getKey()
  if (!key) throw new Error('Encrypted API response received but VITE_API_ENCRYPTION_KEY is not configured')

  const parts = value.slice(PREFIX.length).split('.')
  if (parts.length !== 2) throw new Error('Invalid encrypted API response')

  const plaintext = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: bytesToArrayBuffer(base64UrlToBytes(parts[0])) },
    key,
    bytesToArrayBuffer(base64UrlToBytes(parts[1]))
  )

  return JSON.parse(new TextDecoder().decode(plaintext))
}

export function apiEncryptionEnabled(): boolean {
  return Boolean(KEY_ENV)
}
