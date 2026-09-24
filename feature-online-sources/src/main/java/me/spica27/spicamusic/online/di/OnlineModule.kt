package me.spica27.spicamusic.online.di

import me.spica27.spicamusic.online.OnlineSourcePort
import me.spica27.spicamusic.online.download.OnlineDownloader
import me.spica27.spicamusic.online.lx.LxHost
import me.spica27.spicamusic.online.lx.LxOnlineSource
import me.spica27.spicamusic.online.lx.LxScriptStore
import me.spica27.spicamusic.online.settings.OnlineSettings
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * 在线音源相关依赖。宿主在创建时即装载内置音源与已启用的自定义源脚本，
 * 上层只依赖 OnlineSourcePort，不关心某个来源是内置实现还是用户脚本。
 */
val onlineModule = module {
  single { OnlineSettings(androidContext()) }
  single { LxScriptStore(androidContext()) }
  single { LxHost(androidContext()) }
  single<OnlineSourcePort> {
    val host = get<LxHost>()
    val settings = get<OnlineSettings>()
    val scripts = get<LxScriptStore>()
    host.start(scripts.enabled())
    LxOnlineSource(host) { settings.enabledPlatforms() }
  }
  single { OnlineDownloader(androidContext(), get(), get()) }
}