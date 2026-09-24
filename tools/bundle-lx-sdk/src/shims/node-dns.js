// Node dns 垫片：移动端没有预解析需求，直接回调失败让调用方忽略。
export const lookup = (hostname, options, callback) => {
  const cb = typeof options === 'function' ? options : callback
  if (typeof cb === 'function') cb(new Error('dns lookup is not available'))
}

export default { lookup }