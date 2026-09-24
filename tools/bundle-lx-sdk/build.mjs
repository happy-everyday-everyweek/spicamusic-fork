// 用 esbuild 把洛雪内置音源打包成单个文件，供 QuickJS 加载。
// 落雪源码里对宿主（Electron / Node）的依赖通过垫片替换，音源本体原样参与打包。
import { build } from 'esbuild'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const here = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(here, '..', '..')
const lxRoot = path.join(repoRoot, 'third_party', 'lx-music')
const outFile = path.join(repoRoot, 'app', 'src', 'main', 'assets', 'lx', 'musicSdk.js')

const shim = (name) => path.join(here, 'src', 'shims', `${name}.js`)
const runtime = (name) => path.join(here, 'src', 'runtime', `${name}.js`)

// 只替换落雪源码里那几个明确的宿主模块说明符，避免误伤音源目录自身的相对导入。
const aliases = new Map([
  ['../../request', shim('request')],
  ['../../index', shim('index')],
  ['../options', shim('options')],
  ['../utils', shim('utils')],
  ['../api-source', shim('api-source')],
  ['./api-source', shim('api-source')],
  ['@renderer/store', shim('store')],
  ['@renderer/utils', shim('index')],
  ['@common/utils/common', shim('common')],
  ['@common/utils/lyricUtils/kg', path.join(lxRoot, 'support', 'lyricUtils', 'kg.js')],
  ['@common/utils/lyricUtils/util', shim('lyric-util')],
  ['crypto', shim('node-crypto')],
  ['dns', shim('node-dns')],
  ['zlib', shim('zlib')],
])

const aliasPlugin = {
  name: 'lx-host-shims',
  setup(build) {
    build.onResolve({ filter: /.*/ }, (args) => {
      const mapped = aliases.get(args.path)
      if (mapped) return { path: mapped }
      return null
    })
  },
}

await build({
  entryPoints: [path.join(here, 'src', 'entry.js')],
  bundle: true,
  format: 'iife',
  platform: 'neutral',
  target: 'es2020',
  charset: 'utf8',
  legalComments: 'none',
  outfile: outFile,
  plugins: [aliasPlugin],
  define: {
    'process.env.NODE_ENV': '"production"',
    global: 'globalThis',
  },
  logLevel: 'info',
})

console.log(`LX music SDK bundle: ${outFile}`)
