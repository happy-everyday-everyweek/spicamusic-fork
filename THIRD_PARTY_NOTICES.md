# 第三方组件与许可声明

本仓库以 GPL-3.0 发布。下列上游项目的代码或资源被引入、移植或改编进本仓库，其版权归各上游作者所有，许可原文保存在 `licenses/` 目录。

## SPICaMusic（柠檬音乐上游）

- 来源：https://github.com/yangSpica27/SPICaMusic_Android
- 许可：MIT，见 `licenses/SPICaMusic-MIT.txt`
- 使用方式：本仓库以其实体代码为基线，保留其包名 `me.spica27.spicamusic` 与原签名密钥，以便覆盖安装。

## FuoEvolve

- 来源：https://github.com/feeluown/FuoEvolve
- 许可：GPL-3.0，见 `licenses/FuoEvolve-GPL-3.0.txt`
- 使用方式：移植其音源实现（网易云音乐、QQ 音乐、哔哩哔哩、YouTube Music）与下载、本地库相关的契约与逻辑。

## LX Music（洛雪音乐）

- 来源：https://github.com/lyswhut/lx-music-desktop 与 https://github.com/lyswhut/lx-music-mobile
- 许可：Apache-2.0，见 `licenses/lx-music-Apache-2.0.txt`
- 使用方式：移植其音源实现（酷我、酷狗、QQ 音乐、网易云音乐、咪咕、百度、虾米）、自定义源脚本宿主（QuickJS 宿主、预置脚本与原生桥的接口约定）以及设置项的取值口径（音质档位、文件命名档位）。

## lx-music-doc

- 来源：https://github.com/lyswhut/lx-music-doc
- 许可：MIT
- 使用方式：自定义源脚本 API 的接口约定依据其文档实现。

## 其他运行时依赖

- crypto-js：用于洛雪音源实现所需的散列与加密，随其上游许可（MIT）分发，仅在 JS 侧打包产物中使用。
- QuickJS 的 Android 封装（`wang.harlon.quickjs:wrapper-android`）：用于运行自定义源脚本，遵循其自身许可。

## 不随仓库分发的内容

第三方的洛雪自定义源脚本不包含在本仓库中，由使用者自行导入。本仓库不内置任何此类脚本。
