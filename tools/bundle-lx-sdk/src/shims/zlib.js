// zlib 垫片：DEFLATE 与 zlib 解压交给原生实现（java.util.zip）。
import { Buffer } from '../runtime/buffer.js'
import { zlibCall } from '../runtime/native.js'

const toBuffer = (result) => {
  if (!result) return Buffer.alloc(0)
  if (typeof result === 'string') return Buffer.from(result, 'base64')
  if (result.data) return Buffer.from(result.data, 'base64')
  return Buffer.alloc(0)
}

export const inflate = (data, callback) => {
  zlibCall('inflate', Buffer.from(data).toString('base64'))
    .then((result) => callback(null, toBuffer(result)))
    .catch((err) => callback(err))
}

export const inflateRaw = (data, callback) => {
  zlibCall('inflateRaw', Buffer.from(data).toString('base64'))
    .then((result) => callback(null, toBuffer(result)))
    .catch((err) => callback(err))
}

export const deflateRaw = (data, callback) => {
  zlibCall('deflateRaw', Buffer.from(data).toString('base64'))
    .then((result) => callback(null, toBuffer(result)))
    .catch((err) => callback(err))
}

export const gunzip = (data, callback) => {
  zlibCall('gunzip', Buffer.from(data).toString('base64'))
    .then((result) => callback(null, toBuffer(result)))
    .catch((err) => callback(err))
}

export const constants = {
  Z_NO_FLUSH: 0,
  Z_PARTIAL_FLUSH: 1,
  Z_SYNC_FLUSH: 2,
  Z_FULL_FLUSH: 3,
  Z_FINISH: 4,
  Z_OK: 0,
  Z_STREAM_END: 1,
  Z_DEFAULT_COMPRESSION: -1,
  Z_BEST_SPEED: 1,
  Z_BEST_COMPRESSION: 9,
}

export default { inflate, inflateRaw, deflateRaw, gunzip, constants }