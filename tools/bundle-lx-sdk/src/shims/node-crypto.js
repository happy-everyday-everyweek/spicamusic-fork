// Node crypto 垫片：只实现 md5 / sha1 / sha256，供落雪源码里做签名用。
import CryptoJS from 'crypto-js'

const algo = (name) => {
  const key = String(name).toLowerCase().replace(/-/g, '')
  if (key === 'md5') return CryptoJS.MD5
  if (key === 'sha1') return CryptoJS.SHA1
  if (key === 'sha256') return CryptoJS.SHA256
  if (key === 'sha512') return CryptoJS.SHA512
  return CryptoJS.MD5
}

const createHash = (name) => {
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
  return {
    update(value) {
      content += typeof value === 'string' ? value : String(value)
      return this
    },
    digest(encoding = 'hex') {
      const result = CryptoJS.HmacMD5(content, String(key))
      return encoding === 'base64' ? result.toString(CryptoJS.enc.Base64) : result.toString(CryptoJS.enc.Hex)
    },
  }
}

export default { createHash, createHmac }