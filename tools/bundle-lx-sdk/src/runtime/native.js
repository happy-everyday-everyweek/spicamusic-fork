// 原生桥：JS 侧调用 Kotlin 提供的能力。
// 约定与洛雪移动版一致：JS 通过 globalThis.__lx_native_call__(action, payloadJson) 发起调用，
// 返回一个 requestId；Kotlin 侧完成后回调 globalThis.__lx_native_result__(requestId, resultJson)。

let seq = 0
const pending = new Map()

export function nativeCall(action, payload) {
  const id = `n${++seq}`
  const promise = new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject })
  })
  try {
    globalThis.__lx_native_call__(id, action, JSON.stringify(payload ?? {}))
  } catch (err) {
    pending.delete(id)
    return Promise.reject(err)
  }
  return promise
}

// 由 Kotlin 侧调用
globalThis.__lx_native_result__ = function (id, resultJson) {
  const entry = pending.get(id)
  if (!entry) return
  pending.delete(id)
  let parsed
  try {
    parsed = resultJson ? JSON.parse(resultJson) : null
  } catch (err) {
    entry.reject(new Error(`原生返回内容无法解析: ${err && err.message}`))
    return
  }
  if (parsed && parsed.error) {
    entry.reject(new Error(parsed.error))
    return
  }
  entry.resolve(parsed ? parsed.data : null)
}

export function httpRequest({ url, method = 'GET', headers = {}, body = null, timeout = 15000 }) {
  return nativeCall('http', { url, method, headers, body, timeout })
}

export function zlibCall(kind, bytesBase64) {
  return nativeCall('zlib', { kind, data: bytesBase64 })
}

export function cryptoCall(kind, payload) {
  return nativeCall('crypto', { kind, ...payload })
}

export function log(message, level = 'info') {
  try {
    nativeCall('log', { level, message: String(message) })
  } catch (err) {
    // 日志失败不影响主流程
  }
}
