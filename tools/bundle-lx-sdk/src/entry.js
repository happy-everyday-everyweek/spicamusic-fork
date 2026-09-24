// 打包入口：安装运行时垫片，加载落雪内置音源，并把它们暴露为统一的原生可调用接口。
import { installBuffer } from './runtime/buffer.js'
import { log } from './runtime/native.js'
import sdk from '../../../third_party/lx-music/musicSdk/index.js'

installBuffer()

const sourceOrder = ['kw', 'kg', 'tx', 'wy', 'mg', 'bd', 'xm']

const missingSource = (id) => {
  throw new Error(`未知音源: ${id}`)
}

const host = {
  version: 1,

  sources() {
    return sourceOrder
      .filter((id) => sdk[id])
      .map((id) => ({ id, name: (sdk.sources.find((s) => s.id === id) || {}).name || id }))
  },

  async init() {
    try {
      await sdk.init()
      return true
    } catch (err) {
      log(`音源初始化失败: ${(err && err.message) || err}`, 'warn')
      return false
    }
  },

  async search(sourceId, keyword, page = 1, limit = 25) {
    const source = sdk[sourceId]
    if (!source || !source.musicSearch) return missingSource(sourceId)
    const result = await source.musicSearch.search(keyword, page, limit)
    return result || { list: [], total: 0, allPage: 1 }
  },

  async searchAll(keyword, limit = 25) {
    const tasks = sourceOrder
      .filter((id) => sdk[id] && sdk[id].musicSearch)
      .map((id) =>
        host
          .search(id, keyword, 1, limit)
          .then((result) => ({ source: id, result }))
          .catch((err) => ({ source: id, error: (err && err.message) || String(err) })),
      )
    return Promise.all(tasks)
  },

  async findMusic(payload) {
    return sdk.findMusic(payload)
  },

  async getMusicUrl(sourceId, songInfo, quality) {
    const source = sdk[sourceId]
    if (!source || !source.getMusicUrl) return missingSource(sourceId)
    return source.getMusicUrl(songInfo, quality)
  },

  async getLyric(sourceId, songInfo, isGetLyricx = false) {
    const source = sdk[sourceId]
    if (!source || !source.getLyric) return null
    return source.getLyric(songInfo, isGetLyricx)
  },

  async getPic(sourceId, songInfo) {
    const source = sdk[sourceId]
    if (!source || !source.getPic) return null
    return source.getPic(songInfo)
  },

  detailPageUrl(sourceId, songInfo) {
    const source = sdk[sourceId]
    if (!source || !source.getMusicDetailPageUrl) return null
    return source.getMusicDetailPageUrl(songInfo)
  },

  async handleMusicInfo(sourceId, songInfo) {
    const source = sdk[sourceId]
    if (!source || !source.handleMusicInfo) return songInfo
    return source.handleMusicInfo(songInfo)
  },
}

globalThis.LxMusicSdk = host

export default host