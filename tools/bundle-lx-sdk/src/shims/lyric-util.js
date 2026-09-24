// 歌词工具：kg.js 依赖 './util' 提供的 decodeName。
// 原实现用 DOMParser 处理 HTML 实体，这里改成实体表处理，避免依赖浏览器 API。
const ENTITIES = {
  amp: '&',
  lt: '<',
  gt: '>',
  quot: '"',
  apos: "'",
  nbsp: ' ',
  hellip: '…',
  mdash: '—',
  ndash: '–',
  ldquo: '“',
  rdquo: '”',
  lsquo: '‘',
  rsquo: '’',
}

export const decodeName = (str = '') => {
  if (!str) return ''
  return String(str)
    .replace(/&#(\d+);/g, (_, code) => String.fromCharCode(Number(code)))
    .replace(/&#x([0-9a-fA-F]+);/g, (_, code) => String.fromCharCode(parseInt(code, 16)))
    .replace(/&([a-zA-Z]+);/g, (match, name) => (ENTITIES[name] !== undefined ? ENTITIES[name] : match))
}

export default { decodeName }