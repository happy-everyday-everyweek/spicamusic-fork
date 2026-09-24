// 落雪 httpFetch 的垫片：保持与原实现相同的返回结构（{ promise, cancelHttp }），
// 底层改用原生 HTTP 能力，不再依赖 Node 的 needle。
import { httpRequest } from '../runtime/native.js'

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
  obj.promise = httpRequest({
    url,
    method: (options.method || 'get').toUpperCase(),
    headers: options.headers || {},
    body: options.body ?? options.data ?? null,
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