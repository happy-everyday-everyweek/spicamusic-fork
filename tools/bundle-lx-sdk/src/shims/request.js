// 落雪 httpFetch 的垫片：保持与原实现相同的返回结构（{ promise, cancelHttp }），
// 底层改用原生 HTTP 能力，不再依赖 Node 的 needle。
import { httpRequest } from '../runtime/native.js'
// 照搬洛雪移动版：每个请求都带默认 UA，缺了它不少源会被服务端拒绝。
const defaultHeaders = {
  'User-Agent':
    'Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36',
}
const mergeHeaders = (headers) => ({ ...defaultHeaders, ...(headers || {}) })

const normalizeResp = (raw) => {
  const resp = {
    statusCode: raw ? raw.statusCode : 0,
    statusMessage: raw ? raw.statusMessage ?? '' : '',
    headers: (raw && raw.headers) || {},
    raw: raw ? raw.body ?? '' : '',
  }
  resp.body = resp.raw
  try {
    resp.body = JSON.parse(resp.raw)
  } catch (_) {
    // 保持字符串
  }
  return resp
}

const buildPromise = (url, options = {}) => {
  const obj = {
    isCancelled: false,
    cancelHttp: () => {
      obj.isCancelled = true
    },
  }
  // needle 会把对象类型的 data 序列化成表单；原生桥只接受字符串，
  // 不处理的话 POST 会发出 "[object Object]"，音源就整体拿不到数据。
  const rawBody = options.body ?? options.data ?? null
  let body = rawBody
  let headers = options.headers || {}
  if (rawBody != null && typeof rawBody !== 'string') {
    body = new URLSearchParams(rawBody).toString()
    const hasContentType = Object.keys(headers).some((key) => key.toLowerCase() === 'content-type')
    if (!hasContentType) {
      headers = { ...headers, 'content-type': 'application/x-www-form-urlencoded; charset=UTF-8' }
    }
  }
  obj.promise = httpRequest({
    url,
    method: (options.method || 'get').toUpperCase(),
    headers: mergeHeaders(headers),
    body,
    timeout: options.timeout || 15000,
  })
    .then(normalizeResp)
    .catch((err) => {
      if (err && err.message === 'socket hang up') {
        return Promise.reject(new Error('无法连接服务器'))
      }
      return Promise.reject(err)
    })
  return obj
}

export const httpFetch = (url, options = { method: 'get' }) => buildPromise(url, options)

export const cancelHttp = (requestObj) => {
  if (!requestObj || !requestObj.cancelHttp) return
  requestObj.cancelHttp()
}

export const http = (url, options, cb) => {
  if (typeof options === 'function') {
    cb = options
    options = {}
  }
  const obj = httpFetch(url, options)
  obj.promise.then((resp) => cb(null, resp, resp.body)).catch((err) => cb(err, null, null))
  return obj.cancelHttp
}

export const httpGet = (url, options, callback) => http(url, { ...(typeof options === 'object' ? options : {}), method: 'get' }, callback)

export const httpPost = (url, data, options, callback) =>
  http(url, { ...(typeof options === 'object' ? options : {}), method: 'post', body: data }, callback)

export const checkUrl = (url, options = {}) =>
  httpFetch(url, { ...options, method: 'head' }).promise.then((resp) => {
    if (resp.statusCode === 200) return undefined
    throw new Error(String(resp.statusCode))
  })

export default { httpFetch, http, httpGet, httpPost, checkUrl, cancelHttp }