package me.spica27.spicamusic.common.entity

import androidx.compose.runtime.Immutable

/**
 * 取色方式：主题色的来源。
 *
 * 动态取色：跟随系统动态色（Android 12 及以上从壁纸取色）；封面取色：从当前歌曲封面提取主色。
 */
@Immutable
sealed class ColorSource(
    val value: String,
    val name: String,
) {
    object Dynamic : ColorSource(
        "dynamic",
        "动态取色",
    )

    object Cover : ColorSource(
        "cover",
        "封面取色",
    )

    override fun toString(): String = name

    companion object {
        fun fromString(value: String): ColorSource =
            when (value) {
                Dynamic.value -> Dynamic
                Cover.value -> Cover
                else -> Cover
            }

        val presets: List<ColorSource>
            get() = listOf(Dynamic, Cover)
    }
}
