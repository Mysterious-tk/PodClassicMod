package com.example.podclassic.view

import android.content.Context
import com.example.podclassic.base.Core
import com.example.podclassic.media.CrossfeedLevel
import com.example.podclassic.media.EqualizerPreset
import com.example.podclassic.media.TubeAmpPreset
import com.example.podclassic.service.MediaPresenter
import com.example.podclassic.storage.SPManager
import com.example.podclassic.widget.OnSwitchListener
import com.example.podclassic.widget.RecyclerListView
import com.example.podclassic.widget.RecyclerListView.Item
import com.example.podclassic.widget.RecyclerListView.OnItemClickListener
import com.example.podclassic.widget.SwitchBar

/** One home for the app-owned, device-independent audio processing chain. */
class AudioEffectsView(context: Context) : ItemListView(
    context,
    createItems(context),
    "音效设置",
    null
) {
    companion object {
        private fun createItems(context: Context): ArrayList<Item> = arrayListOf(
            SwitchBar("音量标准化", SPManager.SP_VOLUME_NORMALIZATION_ENABLED, false, reloadListener()),
            Item("音量标准化设置", openNormalizationSettings(context), true),
            SwitchBar("夜间动态音量", SPManager.SP_TOM_STEADY_ENABLED, false, reloadListener()),
            Item("夜间动态音量设置", openNightSettings(context), true),
            SwitchBar("均衡器", SPManager.SP_EQUALIZER_ENABLED, false, reloadListener()),
            Item("均衡器设置", openEqualizerSettings(context), equalizerPreset().title),
            SwitchBar("Clear Bass", SPManager.SP_CLEAR_BASS_ENABLED, false, reloadListener()),
            Item("Clear Bass 设置", cycleInt(
                SPManager.SP_CLEAR_BASS_LEVEL, intArrayOf(1, 2, 3, 4, 5), "级", 2
            ), levelText(SPManager.SP_CLEAR_BASS_LEVEL, 2)),
            SwitchBar("模拟音色", SPManager.SP_TUBE_AMP_ENABLED, false, reloadListener()),
            Item("模拟音色设置", openAnalogSettings(context), tubePreset().displayName),
            SwitchBar("耳机 Crossfeed", SPManager.SP_CROSSFEED_ENABLED, false, reloadListener()),
            Item("Crossfeed 设置", cycleCrossfeed(), crossfeedLevel().title)
        )

        private fun reloadListener() = object : OnSwitchListener {
            override fun onSwitch() = MediaPresenter.reloadAudioEffects()
        }

        private fun openNormalizationSettings(context: Context) = object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                val target = SPManager.getFloat(SPManager.SP_VOLUME_NORMALIZATION_TARGET_DB, -16f)
                val boost = SPManager.getFloat(SPManager.SP_VOLUME_NORMALIZATION_MAX_BOOST_DB, 6f)
                Core.addView(ItemListView(context, arrayListOf(
                    Item("目标电平", cycleFloat(
                        SPManager.SP_VOLUME_NORMALIZATION_TARGET_DB,
                        floatArrayOf(-18f, -16f, -14f), " dB", -16f
                    ), "${target.toInt()} dB"),
                    Item("最大提升", cycleFloat(
                        SPManager.SP_VOLUME_NORMALIZATION_MAX_BOOST_DB,
                        floatArrayOf(3f, 6f, 9f), " dB", 6f
                    ), "${boost.toInt()} dB")
                ), "音量标准化", null))
                return true
            }
        }

        private fun openNightSettings(context: Context) = object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                Core.addView(ItemListView(context, arrayListOf(
                    Item("强度", cycleFloat(
                        SPManager.SP_TOM_STEADY_TARGET_LEVEL,
                        floatArrayOf(0.12f, 0.16f, 0.19f), "", 0.16f,
                        labels = mapOf(0.12f to "轻微", 0.16f to "自然", 0.19f to "明显")
                    ), nightStrength()),
                    Item("最大提升", cycleFloat(
                        SPManager.SP_TOM_STEADY_MAX_GAIN,
                        floatArrayOf(3f, 6f, 9f), " dB", 6f
                    ), "${SPManager.getFloat(SPManager.SP_TOM_STEADY_MAX_GAIN, 6f).toInt()} dB"),
                    Item("响应速度", cycleNightResponse(), nightResponse())
                ), "夜间动态音量", null))
                return true
            }
        }

        private fun openEqualizerSettings(context: Context) = object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                val parentList = listView
                val presetItems = EqualizerPreset.values().mapIndexed { presetIndex, preset ->
                    Item(preset.title, object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                            SPManager.setInt(SPManager.SP_EQUALIZER, presetIndex)
                            MediaPresenter.reloadAudioEffects()
                            parentList.getCurrentItem().rightText = preset.title
                            Core.removeView()
                            return true
                        }
                    }, false)
                }.toCollection(ArrayList())
                presetItems.add(Item("效果强度", cycleFloat(
                    SPManager.SP_EQUALIZER_STRENGTH,
                    floatArrayOf(0.5f, 0.75f, 1f), "", 1f,
                    labels = mapOf(0.5f to "轻微", 0.75f to "自然", 1f to "完整")
                ), equalizerStrength()))
                Core.addView(ItemListView(context, presetItems, "均衡器", null))
                return true
            }
        }

        private fun openAnalogSettings(context: Context) = object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                val parentList = listView
                val presets = TubeAmpPreset.values().filter { it != TubeAmpPreset.NONE }
                val items = presets.map { preset ->
                    Item(preset.displayName, object : OnItemClickListener {
                        override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                            SPManager.setInt(SPManager.SP_TUBE_AMP_PRESET, preset.ordinal)
                            MediaPresenter.reloadAudioEffects()
                            parentList.getCurrentItem().rightText = preset.displayName
                            Core.removeView()
                            return true
                        }
                    }, false)
                }.toCollection(ArrayList())
                items.add(Item("温暖度", cycleFloat(
                    SPManager.SP_TUBE_AMP_WARMTH, floatArrayOf(0.25f, 0.5f, 0.75f), "", 0.5f,
                    labels = mapOf(0.25f to "轻微", 0.5f to "自然", 0.75f to "明显")
                ), analogAmount(SPManager.SP_TUBE_AMP_WARMTH, 0.5f)))
                items.add(Item("饱和度", cycleFloat(
                    SPManager.SP_TUBE_AMP_SATURATION, floatArrayOf(0.2f, 0.4f, 0.6f), "", 0.4f,
                    labels = mapOf(0.2f to "轻微", 0.4f to "自然", 0.6f to "明显")
                ), analogAmount(SPManager.SP_TUBE_AMP_SATURATION, 0.4f)))
                Core.addView(ItemListView(context, items, "模拟音色", null))
                return true
            }
        }

        private fun cycleCrossfeed() = object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                val next = (SPManager.getInt(SPManager.SP_CROSSFEED_LEVEL) + 1) % CrossfeedLevel.values().size
                SPManager.setInt(SPManager.SP_CROSSFEED_LEVEL, next)
                MediaPresenter.reloadAudioEffects()
                listView.getCurrentItem().rightText = CrossfeedLevel.values()[next].title
                return true
            }
        }

        private fun cycleNightResponse() = object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                val current = SPManager.getFloat(SPManager.SP_TOM_STEADY_ATTACK_TIME, 80f)
                val next = when (current) { 150f -> 40f; 40f -> 80f; else -> 150f }
                val release = when (next) { 40f -> 500f; 150f -> 1_500f; else -> 900f }
                SPManager.setFloat(SPManager.SP_TOM_STEADY_ATTACK_TIME, next)
                SPManager.setFloat(SPManager.SP_TOM_STEADY_RELEASE_TIME, release)
                MediaPresenter.reloadAudioEffects()
                listView.getCurrentItem().rightText = nightResponse()
                return true
            }
        }

        private fun cycleFloat(
            key: String,
            values: FloatArray,
            suffix: String,
            default: Float,
            labels: Map<Float, String> = emptyMap()
        ) = object : OnItemClickListener {
            override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                val current = SPManager.getFloat(key, default)
                val currentIndex = values.indexOfFirst { it == current }.let { if (it < 0) 0 else it }
                val next = values[(currentIndex + 1) % values.size]
                SPManager.setFloat(key, next)
                MediaPresenter.reloadAudioEffects()
                listView.getCurrentItem().rightText = labels[next] ?: "${next.toInt()}$suffix"
                return true
            }
        }

        private fun cycleInt(key: String, values: IntArray, suffix: String, default: Int) =
            object : OnItemClickListener {
                override fun onItemClick(index: Int, listView: RecyclerListView): Boolean {
                    val current = SPManager.getInt(key).let { if (it in values) it else default }
                    val next = values[(values.indexOf(current) + 1) % values.size]
                    SPManager.setInt(key, next)
                    MediaPresenter.reloadAudioEffects()
                    listView.getCurrentItem().rightText = "$next$suffix"
                    return true
                }
            }

        private fun equalizerPreset() = EqualizerPreset.values()[
            SPManager.getInt(SPManager.SP_EQUALIZER).coerceIn(0, EqualizerPreset.values().lastIndex)
        ]
        private fun tubePreset() = TubeAmpPreset.values()[
            SPManager.getInt(SPManager.SP_TUBE_AMP_PRESET).coerceIn(0, TubeAmpPreset.values().lastIndex)
        ]
        private fun crossfeedLevel() = CrossfeedLevel.values()[
            SPManager.getInt(SPManager.SP_CROSSFEED_LEVEL).coerceIn(0, CrossfeedLevel.values().lastIndex)
        ]
        private fun levelText(key: String, default: Int) =
            "${SPManager.getInt(key).let { if (it in 1..5) it else default }}级"
        private fun nightStrength() = when (SPManager.getFloat(SPManager.SP_TOM_STEADY_TARGET_LEVEL, 0.16f)) {
            0.12f -> "轻微"; 0.19f -> "明显"; else -> "自然"
        }
        private fun nightResponse() = when (SPManager.getFloat(SPManager.SP_TOM_STEADY_ATTACK_TIME, 80f)) {
            40f -> "快速"; 150f -> "平缓"; else -> "自然"
        }
        private fun equalizerStrength() = when (SPManager.getFloat(SPManager.SP_EQUALIZER_STRENGTH, 1f)) {
            0.5f -> "轻微"; 0.75f -> "自然"; else -> "完整"
        }
        private fun analogAmount(key: String, default: Float) = when (SPManager.getFloat(key, default)) {
            0.25f, 0.2f -> "轻微"; 0.75f, 0.6f -> "明显"; else -> "自然"
        }
    }
}
