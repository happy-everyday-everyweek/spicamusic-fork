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
  // 照搬洛雪移动版：POST 且未显式给出类型时，对象体一律按 JSON 发送；
  // 只有明确传 form 的调用才编成表单。原生桥只接受字符串，必须在这里序列化。
  let headers = { Accept: 'application/json', ...(options.headers || {}) }
  const headerName = (name) =>
    Object.keys(headers).find((key) => key.toLowerCase() === name.toLowerCase())
  const contentType = () => {
    const key = headerName('content-type')
    return key ? String(headers[key]).toLowerCase() : ''
  }
  let body = options.body ?? options.data ?? null
  const method = (options.method || 'get').toUpperCase()
  if (options.form && typeof options.form === 'object') {
    if (!headerName('content-type')) headers['content-type'] = 'application/x-www-form-urlencoded; charset=UTF-8'
    body = new URLSearchParams(options.form).toString()
  } else if (method === 'POST' && !headerName('content-type') && body != null) {
    headers['content-type'] = 'application/json'
  }
  if (body != null && typeof body !== 'string') {
    if (contentType().includes('application/json')) body = JSON.stringify(body)
    else body = new URLSearchParams(body).toString()
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