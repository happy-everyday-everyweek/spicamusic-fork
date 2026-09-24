// store 垫片：落雪这里原本读的是 Electron 的全局状态（当前音源、用户音源、代理）。
// 我们固定使用“用户音源”分支，并把请求桥接到原生侧的脚本宿主。
import { nativeCall } from '../runtime/native.js'

export const apiSource = { value: 'user_api' }

export const proxy = { enable: false, host: '', port: '', envProxy: null }

const buildApi = (source) => ({
  getMusicUrl: (songInfo, quality) => nativeCall('musicUrl', { source, songInfo, quality }),
  getLyric: (songInfo, isGetLyricx = false) => nativeCall('lyric', { source, songInfo, isGetLyricx }),
  getPic: (songInfo) => nativeCall('pic', { source, songInfo }),
  getMusicInfo: (songInfo) => nativeCall('musicInfo', { source, songInfo }),
})

const apisProxy = new Proxy({}, {
  get: (_target, source) => buildApi(String(source)),
})

export const userApi = {
  apis: apisProxy,
  sources: {},
  init: () => Promise.resolve(),
}

export default { apiSource, userApi, proxy }