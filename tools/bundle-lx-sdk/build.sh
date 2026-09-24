#!/usr/bin/env bash
# 打包洛雪音乐内置音源实现为单个 JS 文件，输出到 app/src/main/assets/lx/musicSdk.js
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"

cd "$here"

if [ ! -d node_modules ]; then
  npm install --no-audit --no-fund
fi

mkdir -p "$root/app/src/main/assets/lx"
node ./build.mjs
ls -l "$root/app/src/main/assets/lx/musicSdk.js"
