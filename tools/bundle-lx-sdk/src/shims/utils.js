// 落雪 musicSdk/utils 垫片：原实现依赖 Node 的 crypto 与 dns。
import CryptoJS from 'crypto-js'
import { decodeName } from './lyric-util.js'

export { decodeName }

export const toMD5 = (str) => CryptoJS.MD5(String(str)).toString()

// 移动端不做 DNS 预解析，直接让调用方走系统解析。
export const getHostIp = () => undefined

export const dnsLookup = (hostname, options, callback) => {
  const cb = typeof options === 'function' ? options : callback
  if (typeof cb === 'function') cb(new Error('dns lookup is not available'))
}

export const formatSingerName = (singers, nameKey = 'name', join = '、') => {
  if (Array.isArray(singers)) {
    const names = []
    for (const item of singers) {
      const name = item ? item[nameKey] : null
      if (name) names.push(name)
    }
    return decodeName(names.join(join))
  }
  return decodeName(String(singers ?? ''))
}