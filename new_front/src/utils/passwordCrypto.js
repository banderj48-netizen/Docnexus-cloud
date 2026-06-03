/**
 * 密码传输加密工具。
 *
 * 前端只把 RSA-OAEP 加密后的密码提交给后端，避免请求体中出现明文密码。
 */
let cachedPublicKey = null

function base64ToBytes(base64) {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index)
  }
  return bytes
}

function bytesToBase64(bytes) {
  let binary = ''
  const chunkSize = 0x8000
  for (let index = 0; index < bytes.length; index += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(index, index + chunkSize))
  }
  return btoa(binary)
}

async function importPublicKey(publicKeyBase64) {
  return crypto.subtle.importKey(
    'spki',
    base64ToBytes(publicKeyBase64),
    {
      name: 'RSA-OAEP',
      hash: 'SHA-256',
    },
    false,
    ['encrypt']
  )
}

export async function encryptPassword(password, loadPublicKey) {
  if (!password) {
    throw new Error('密码不能为空')
  }

  if (!cachedPublicKey) {
    const publicKeyInfo = await loadPublicKey()
    cachedPublicKey = await importPublicKey(publicKeyInfo.publicKey)
  }

  const encryptedBytes = await crypto.subtle.encrypt(
    { name: 'RSA-OAEP' },
    cachedPublicKey,
    new TextEncoder().encode(password)
  )

  return bytesToBase64(new Uint8Array(encryptedBytes))
}
