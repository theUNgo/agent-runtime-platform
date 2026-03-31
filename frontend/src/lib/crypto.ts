import { apiFetch } from './api'

interface PublicKeyPayload {
  algorithm: string
  keyFormat: string
  publicKey: string
}

let cachedPublicKey: PublicKeyPayload | null = null

// 浏览器侧只负责做一次传输加密，真正的私钥始终留在后端。
function base64ToArrayBuffer(value: string): ArrayBuffer {
  const normalized = value.replace(/\s+/g, '')
  const binary = window.atob(normalized)
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index)
  }
  return bytes.buffer
}

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return window.btoa(binary)
}

async function getPublicKey(token?: string): Promise<PublicKeyPayload> {
  if (!cachedPublicKey) {
    cachedPublicKey = await apiFetch<PublicKeyPayload>('/api/security/public-key', {}, token)
  }
  return cachedPublicKey
}

// 只有用户真正修改了 API Key，页面才会调用这里生成密文并提交。
export async function encryptApiKey(apiKey: string, token?: string): Promise<string> {
  const keyPayload = await getPublicKey(token)
  const cryptoKey = await window.crypto.subtle.importKey(
    'spki',
    base64ToArrayBuffer(keyPayload.publicKey),
    {
      name: 'RSA-OAEP',
      hash: 'SHA-256'
    },
    false,
    ['encrypt']
  )

  const encrypted = await window.crypto.subtle.encrypt(
    {
      name: 'RSA-OAEP'
    },
    cryptoKey,
    new TextEncoder().encode(apiKey)
  )
  return arrayBufferToBase64(encrypted)
}
