// api-source 垫片：落雪的原逻辑是从“音源接口”里取某个平台的取链实现。
// 在移动端我们把这一层接到自定义源脚本宿主上：apis(source) 返回的对象会把
// getMusicUrl / getLyric / getPic 请求转交给原生侧运行的脚本。
import apiSourceInfo from '../../../third_party/lx-music/musicSdk/api-source-info.ts'
import { apiSource, userApi } from './store.js'

export const supportQuality = {}

for (const api of apiSourceInfo) {
  supportQuality[api.id] = api.supportQualitys
}

export const apis = (source) => {
  if (/^user_api/.test(apiSource.value)) return userApi.apis[source]
  throw new Error('Api is not found')
}

export default { apis, supportQuality }