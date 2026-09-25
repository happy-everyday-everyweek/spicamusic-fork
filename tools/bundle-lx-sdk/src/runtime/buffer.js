// QuickJS 里没有 Node 的 Buffer，落雪音源大量使用 Buffer.from / toString / subarray，
// 这里用纯 JS 实现够用的子集：utf8 与 base64、hex、binary 之间的互转。

const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

function utf8Encode(str) {
  const out = []
  for (let i = 0; i < str.length; i++) {
    let code = str.charCodeAt(i)
    if (code >= 0xd800 && code <= 0xdbff && i + 1 < str.length) {
      const next = str.charCodeAt(i + 1)
      if (next >= 0xdc00 && next <= 0xdfff) {
        code = ((code - 0xd800) << 10) + (next - 0xdc00) + 0x10000
        i++
      }
    }
    if (code < 0x80) out.push(code)
    else if (code < 0x800) out.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f))
    else if (code < 0x10000) out.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f))
    else out.push(
      0xf0 | (code >> 18),
      0x80 | ((code >> 12) & 0x3f),
      0x80 | ((code >> 6) & 0x3f),
      0x80 | (code & 0x3f),
    )
  }
  return out
}

function utf8Decode(bytes) {
  let out = ''
  for (let i = 0; i < bytes.length;) {
    const b = bytes[i++]
    if (b < 0x80) out += String.fromCharCode(b)
    else if (b < 0xe0) out += String.fromCharCode(((b & 0x1f) << 6) | (bytes[i++] & 0x3f))
    else if (b < 0xf0) {
      out += String.fromCharCode(((b & 0x0f) << 12) | ((bytes[i++] & 0x3f) << 6) | (bytes[i++] & 0x3f))
    } else {
      const code = ((b & 0x07) << 18) | ((bytes[i++] & 0x3f) << 12) | ((bytes[i++] & 0x3f) << 6) | (bytes[i++] & 0x3f)
      const v = code - 0x10000
      out += String.fromCharCode(0xd800 + (v >> 10), 0xdc00 + (v & 0x3ff))
    }
  }
  return out
}

function base64Encode(bytes) {
  let out = ''
  for (let i = 0; i < bytes.length; i += 3) {
    const b0 = bytes[i]
    const b1 = bytes[i + 1]
    const b2 = bytes[i + 2]
    out += B64[b0 >> 2]
    out += B64[((b0 & 3) << 4) | ((b1 ?? 0) >> 4)]
    out += b1 === undefined ? '=' : B64[((b1 & 15) << 2) | ((b2 ?? 0) >> 6)]
    out += b2 === undefined ? '=' : B64[b2 & 63]
  }
  return out
}

function base64Decode(str) {
  const clean = String(str).replace(/[^A-Za-z0-9+/]/g, '')
  const out = []
  for (let i = 0; i < clean.length; i += 4) {
    const c0 = B64.indexOf(clean[i])
    const c1 = B64.indexOf(clean[i + 1])
    const c2 = B64.indexOf(clean[i + 2])
    const c3 = B64.indexOf(clean[i + 3])
    out.push((c0 << 2) | (c1 >> 4))
    if (c2 >= 0) out.push(((c1 & 15) << 4) | (c2 >> 2))
    if (c3 >= 0) out.push(((c2 & 3) << 6) | c3)
  }
  return out
}

export class Buffer extends Uint8Array {
  static from(value, encoding = 'utf8') {
    if (typeof value === 'string') {
      if (encoding === 'base64') return new Buffer(base64Decode(value))
      if (encoding === 'hex') {
        const out = []
        for (let i = 0; i < value.length; i += 2) out.push(parseInt(value.substr(i, 2), 16))
        return new Buffer(out)
      }
      if (encoding === 'binary' || encoding === 'latin1') {
        const out = []
        for (let i = 0; i < value.length; i++) out.push(value.charCodeAt(i) & 0xff)
        return new Buffer(out)
      }
      return new Buffer(utf8Encode(value))
    }
    if (value instanceof ArrayBuffer) return new Buffer(new Uint8Array(value))
    if (ArrayBuffer.isView(value)) return new Buffer(new Uint8Array(value.buffer, value.byteOffset, value.byteLength))
    if (Array.isArray(value)) return new Buffer(value)
    return new Buffer(0)
  }

  static alloc(size) {
    return new Buffer(size)
  }

  static concat(list) {
    let total = 0
    for (const item of list) total += item.length
    const out = new Buffer(total)
    let offset = 0
    for (const item of list) {
      out.set(item, offset)
      offset += item.length
    }
    return out
  }

  static isBuffer(value) {
    return value instanceof Buffer
  }

  toString(encoding = 'utf8', start = 0, end = this.length) {
    const slice = this.subarray(start, end)
    if (encoding === 'base64') return base64Encode(slice)
    if (encoding === 'hex') return Array.from(slice).map((b) => b.toString(16).padStart(2, '0')).join('')
    if (encoding === 'binary' || encoding === 'latin1') {
      return Array.from(slice).map((b) => String.fromCharCode(b)).join('')
    }
    return utf8Decode(slice)
  }

  toJSON() {
    return { type: 'Buffer', data: Array.from(this) }
  }
}

export function installBuffer() {
  if (!globalThis.Buffer) globalThis.Buffer = Buffer
}

// 模块被导入时就安装，保证后续模块（含音源本体）在模块体阶段就能用到 Buffer。
installBuffer()

// 音源脚本里有直接读浏览器全局量的代码（如酷狗签名 vendor 用 navigator），
// 这里补上最小实现，避免导入阶段就报未定义。
if (typeof globalThis.navigator === "undefined") {
  globalThis.navigator = {
    userAgent:
      "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36",
    platform: "Android",
    language: "zh-CN",
  }
}
if (typeof globalThis.window === "undefined") {
  globalThis.window = globalThis
}
if (typeof globalThis.document === "undefined") {
  globalThis.document = {
    createElement: () => ({}),
    getElementsByTagName: () => [],
  }
}
