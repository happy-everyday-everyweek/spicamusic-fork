// Node crypto 垫片：落雪音源用到 md5/sha、AES（酷我签名）与 RSA 公钥加密（部分接口参数）。
// AES 走 crypto-js，RSA 用 BigInt 实现 PKCS#1 v1.5，避免引入原生依赖。
import CryptoJS from 'crypto-js'
import { Buffer } from '../runtime/buffer.js'

const algo = (name) => {
  const key = String(name).toLowerCase().replace(/-/g, '')
  if (key === 'md5') return CryptoJS.MD5
  if (key === 'sha1') return CryptoJS.SHA1
  if (key === 'sha256') return CryptoJS.SHA256
  if (key === 'sha512') return CryptoJS.SHA512
  return CryptoJS.MD5
}

export const createHash = (name) => {
  let content = ''
  return {
    update(value) {
      content += typeof value === 'string' ? value : String(value)
      return this
    },
    digest(encoding = 'hex') {
      const result = algo(name)(content)
      return encoding === 'base64' ? result.toString(CryptoJS.enc.Base64) : result.toString(CryptoJS.enc.Hex)
    },
  }
}

export const createHmac = (name, key) => {
  let content = ''
  const keyStr = String(key)
  const fn = String(name).toLowerCase() === 'sha256' ? CryptoJS.HmacSHA256 : CryptoJS.HmacMD5
  return {
    update(value) {
      content += typeof value === 'string' ? value : String(value)
      return this
    },
    digest(encoding = 'hex') {
      const result = fn(content, keyStr)
      return encoding === 'base64' ? result.toString(CryptoJS.enc.Base64) : result.toString(CryptoJS.enc.Hex)
    },
  }
}

const toWordArray = (bytes) => CryptoJS.enc.Base64.parse(Buffer.from(bytes).toString('base64'))

const aesConfig = (algorithm, iv) => {
  const name = String(algorithm).toLowerCase()
  const config = { padding: CryptoJS.pad.Pkcs7 }
  if (name.includes('ecb')) config.mode = CryptoJS.mode.ECB
  else if (name.includes('ctr')) config.mode = CryptoJS.mode.CTR
  else if (name.includes('cfb')) config.mode = CryptoJS.mode.CFB
  else if (name.includes('ofb')) config.mode = CryptoJS.mode.OFB
  else config.mode = CryptoJS.mode.CBC
  if (!name.includes('ecb') && iv) config.iv = toWordArray(iv)
  return config
}

export const createCipheriv = (algorithm, key, iv) => {
  let input = Buffer.alloc(0)
  const config = aesConfig(algorithm, iv)
  const keyWa = toWordArray(key)
  return {
    update(data) {
      input = Buffer.concat([input, Buffer.from(data)])
      return Buffer.alloc(0)
    },
    final() {
      const encrypted = CryptoJS.AES.encrypt(toWordArray(input), keyWa, config)
      return Buffer.from(encrypted.ciphertext.toString(CryptoJS.enc.Base64), 'base64')
    },
    setAutoPadding() {
      return this
    },
  }
}

export const createDecipheriv = (algorithm, key, iv) => {
  let input = Buffer.alloc(0)
  const config = aesConfig(algorithm, iv)
  const keyWa = toWordArray(key)
  return {
    update(data) {
      input = Buffer.concat([input, Buffer.from(data)])
      return Buffer.alloc(0)
    },
    final() {
      const decrypted = CryptoJS.AES.decrypt({ ciphertext: toWordArray(input) }, keyWa, config)
      return Buffer.from(decrypted.toString(CryptoJS.enc.Base64), 'base64')
    },
    setAutoPadding() {
      return this
    },
  }
}

export const randomBytes = (size) => {
  const out = Buffer.alloc(size)
  for (let i = 0; i < size; i++) out[i] = Math.floor(Math.random() * 256)
  return out
}

// ---- RSA：解析 DER 公钥并以 PKCS#1 v1.5 加密 ----

const readDerLength = (bytes, offset) => {
  let length = bytes[offset]
  let cursor = offset + 1
  if (length & 0x80) {
    const count = length & 0x7f
    length = 0
    for (let i = 0; i < count; i++) length = (length << 8) | bytes[cursor++]
  }
  return { length, cursor }
}

const readDerInteger = (bytes, offset) => {
  const { length, cursor } = readDerLength(bytes, offset)
  let value = 0n
  for (let i = 0; i < length; i++) value = (value << 8n) | BigInt(bytes[cursor + i])
  return { value, cursor: cursor + length }
}

const parsePublicKey = (bytes) => {
  if (bytes[0] !== 0x30) return null
  let cursor = 2
  if (bytes[1] & 0x80) cursor = 2 + (bytes[1] & 0x7f)
  if (bytes[cursor] === 0x30) {
    const inner = readDerLength(bytes, cursor)
    cursor = inner.cursor + inner.length
  }
  if (bytes[cursor] !== 0x03) return null
  const bitString = readDerLength(bytes, cursor)
  cursor = bitString.cursor + 1
  if (bytes[cursor] !== 0x30) return null
  const seq = readDerLength(bytes, cursor)
  const modulus = readDerInteger(bytes, seq.cursor)
  const exponent = readDerInteger(bytes, modulus.cursor)
  return { n: modulus.value, e: exponent.value }
}

const bigIntToBytes = (value, size) => {
  const out = Buffer.alloc(size)
  let v = value
  for (let i = size - 1; i >= 0; i--) {
    out[i] = Number(v & 0xffn)
    v >>= 8n
  }
  return out
}

export const publicEncrypt = (key, data) => {
  const keyBytes =
    typeof key === 'string'
      ? Buffer.from(key.replace(/-----[^-]+-----/g, '').replace(/\s+/g, ''), 'base64')
      : Buffer.from(key)
  const parsed = parsePublicKey(keyBytes)
  const message = Buffer.from(typeof data === 'string' ? data : Buffer.from(data))
  if (!parsed) return message
  const keySize = Math.ceil(parsed.n.toString(2).length / 8)
  if (message.length > keySize - 11) throw new Error('RSA: 数据过长')
  const padding = randomBytes(keySize - message.length - 3)
  const padded = Buffer.concat([
    Buffer.from([0, 2]),
    padding.map((b) => (b === 0 ? 1 : b)),
    Buffer.from([0]),
    message,
  ])
  let value = 0n
  for (const byte of padded) value = (value << 8n) | BigInt(byte)
  let result = 1n
  let base = value % parsed.n
  let exp = parsed.e
  while (exp > 0n) {
    if (exp & 1n) result = (result * base) % parsed.n
    base = (base * base) % parsed.n
    exp >>= 1n
  }
  return bigIntToBytes(result, keySize)
}

export const constants = {
  RSA_PKCS1_PADDING: 1,
  RSA_PKCS1_OAEP_PADDING: 4,
}

export default {
  createHash,
  createHmac,
  createCipheriv,
  createDecipheriv,
  randomBytes,
  publicEncrypt,
  constants,
}