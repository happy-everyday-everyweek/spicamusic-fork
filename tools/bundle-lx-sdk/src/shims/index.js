// 落雪 utils/index 垫片：原实现依赖 Electron 的 window.i18n 与 DOMParser，
// 这里只用音源实际用到的纯函数。
import CryptoJS from 'crypto-js'
import { decodeName } from './lyric-util.js'

export const dateFormat = (time, format = 'Y-M-D h:m:s') => {
  const date = new Date(time)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (value) => String(value).padStart(2, '0')
  const map = {
    Y: String(date.getFullYear()),
    M: pad(date.getMonth() + 1),
    D: pad(date.getDate()),
    h: pad(date.getHours()),
    m: pad(date.getMinutes()),
    s: pad(date.getSeconds()),
  }
  return format.replace(/Y|M|D|h|m|s/g, (token) => map[token])
}

export const formatPlayCount = (num) => {
  if (num > 100000000) return `${Math.trunc(num / 10000000) / 10}亿`
  if (num > 10000) return `${Math.trunc(num / 1000) / 10}万`
  return String(num)
}

export const dateFormat2 = (time) => new Date(time).toISOString()

export const formatPlayTime = (seconds) => {
  if (!Number.isFinite(seconds)) return '00:00'
  const total = Math.trunc(seconds)
  const m = Math.trunc(total / 60)
  const s = total % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

export const sizeFormate = (size) => {
  if (!Number.isFinite(size)) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(2)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(2)} MB`
  return `${(size / 1024 / 1024 / 1024).toFixed(2)} GB`
}

export const toMD5 = (str) => CryptoJS.MD5(String(str)).toString()

export const deduplicationList = (list) => {
  const ids = new Set()
  return list.filter((item) => {
    if (ids.has(item.id)) return false
    ids.add(item.id)
    return true
  })
}

export const decodeName2 = decodeName

export { decodeName }

export default {
  formatPlayCount,
  dateFormat2,
  formatPlayTime,
  sizeFormate,
  toMD5,
  deduplicationList,
  decodeName,
}