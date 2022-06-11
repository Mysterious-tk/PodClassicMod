package com.example.podclassic.storage


import android.preference.PreferenceManager
import com.example.podclassic.R
import com.example.podclassic.base.BaseApplication
import com.example.podclassic.values.Strings
import com.example.podclassic.values.Strings.DISABLE
import com.example.podclassic.values.Strings.KEEP_PLAYING
import com.example.podclassic.values.Strings.MINUTE

object SPManager {
    const val SP_STARTED = "started"
    const val SP_AUDIO_FOCUS = "audio_focus"
    const val SP_RESET_COUNT = "reset_count"
    const val SP_SHOW_TIME = "show_time"
    const val SP_SHOW_LYRIC = "show_lyric"
    const val SP_SHOW_INFO = "show_info"
    const val SP_EQUALIZER = "equalizer"
    const val SP_PLAY_MODE = "play_mode"
    const val SP_PLAY_ALL = "play_all"
    const val SP_COVER_FLOW = "cover_flow"
    const val SP_REPEAT_MODE = "repeat_mode"
    private val sharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(BaseApplication.context)

    fun getBoolean(sp: String?): Boolean {
        return sharedPreferences.getBoolean(sp, false)
    }

    fun setBoolean(sp: String?, value: Boolean) {
        sharedPreferences.edit().putBoolean(sp, value).apply()
    }

    fun getInt(sp: String?): Int {
        return sharedPreferences.getInt(sp, 0)
    }

    fun setInt(sp: String?, value: Int) {
        sharedPreferences.edit().putInt(sp, value).apply()
    }

    fun getString(sp: String?): String? {
        return sharedPreferences.getString(sp, "")
    }

    fun setString(sp: String?, value: String?) {
        sharedPreferences.edit().putString(sp, value).apply()
    }

    fun reset() {
        setBoolean(SP_PLAY_ALL, true)
        setBoolean(SP_AUDIO_FOCUS, true)
        setBoolean(SP_SHOW_TIME, true)
        setBoolean(SP_SHOW_LYRIC, true)
        setInt(Theme.SP_NAME, Theme.RED.id)
        setBoolean(SP_SHOW_INFO, false)
        setBoolean(SP_COVER_FLOW, false)
        setInt(NightMode.SP_NAME, NightMode.DISABLE.id)
        setInt(Sound.SP_NAME, Sound.VIBRATE.id)
        setInt(AutoStop.SP_NAME, AutoStop.DISABLE_ID)
        setInt(SP_PLAY_MODE, 0)
        setInt(SP_REPEAT_MODE, 1)
        setInt(Language.SP_NAME, 0)
        setInt(SP_EQUALIZER, 0)
        var resetCount = getInt(SP_RESET_COUNT)
        resetCount++
        setInt(SP_RESET_COUNT, resetCount)
        sharedPreferences.edit().commit()
    }

    fun save() {
        sharedPreferences.edit().commit()
    }


    enum class Language(val id: Int, private val titleId: Int) {
        AUTO(0, R.string.auto), CN(1, R.string.language_cn), TW(2, R.string.language_tw), EN(
            3,
            R.string.language_en
        );

        companion object {
            const val SP_NAME = "language"
            const val values = 4
            fun getTitle(id: Int): String {
                return when (id) {
                    0 -> AUTO.title
                    1 -> CN.title
                    2 -> TW.title
                    3 -> EN.title
                    else -> AUTO.title
                }
            }
        }

        val title get() = Strings.getString(titleId)

    }

    enum class Theme(val id: Int, private val titleId: Int) {
        WHITE(2, R.string.theme_white), BLACK(1, R.string.theme_black), RED(0, R.string.theme_red);

        companion object {
            const val SP_NAME = "theme_color"
            const val values = 3
            fun getTitle(id: Int): String {
                return when (id) {
                    WHITE.id -> WHITE.title
                    BLACK.id -> BLACK.title
                    RED.id -> RED.title
                    else -> BLACK.title
                }
            }
        }

        val title get() = Strings.getString(titleId)

    }

    enum class NightMode(val id: Int, private val titleId: Int) {
        AUTO(0, R.string.auto), ENABLE(1, R.string.enable), DISABLE(2, R.string.disable);

        companion object {
            const val SP_NAME = "night_mode"
            const val values = 3
            fun getTitle(id: Int): String {
                return when (id) {
                    0 -> AUTO.title
                    1 -> ENABLE.title
                    2 -> DISABLE.title
                    else -> AUTO.title
                }
            }
        }

        val title get() = Strings.getString(titleId)

    }

    enum class Sound(val id: Int, private val titleId: Int) {
        //顺序不能变!
        DISABLE(0, R.string.disable), VIBRATE(2, R.string.vibrate), SOUND(
            1,
            R.string.sound
        ),
        VIBRATE_AND_SOUND(3, R.string.sound_and_vibrate);

        companion object {
            const val SP_NAME = "sound"
            const val values = 4
            fun getTitle(id: Int): String {
                return when (id) {
                    0 -> DISABLE.title
                    1 -> SOUND.title
                    2 -> VIBRATE.title
                    3 -> VIBRATE_AND_SOUND.title
                    else -> DISABLE.title
                }
            }
        }

        val title get() = Strings.getString(titleId)

    }

    object AutoStop {
        val DISABLE_NAME get() = DISABLE
        const val DISABLE_ID = 0
        val M15_NAME get() = "15 $MINUTE"
        const val M15_ID = 1
        val M30_NAME get() = "30 $MINUTE"
        const val M30_ID = 2
        val M60_NAME get() = "60 $MINUTE"
        const val M60_ID = 3
        val M90_NAME get() = "90 $MINUTE"
        const val M90_ID = 4
        val M120_NAME get() = "120 $MINUTE"
        const val M120_ID = 5
        val M240_NAME get() = "240 $MINUTE"
        const val M240_ID = 6
        val FOREVER_NAME get() = KEEP_PLAYING
        const val FOREVER_ID = 7
        const val values = 8
        const val SP_NAME = "auto_stop"
        fun getString(id: Int): String {
            when (id) {
                DISABLE_ID -> return DISABLE_NAME
                M15_ID -> return M15_NAME
                M30_ID -> return M30_NAME
                M60_ID -> return M60_NAME
                M90_ID -> return M90_NAME
                M120_ID -> return M120_NAME
                M240_ID -> return M240_NAME
                FOREVER_ID -> return FOREVER_NAME
            }
            return ""
        }

        fun getMinute(id: Int): Int {
            when (id) {
                DISABLE_ID -> return 0
                M15_ID -> return 15
                M30_ID -> return 30
                M60_ID -> return 60
                M90_ID -> return 90
                M120_ID -> return 120
                M240_ID -> return 240
                FOREVER_ID -> return -1
            }
            return 0
        }
    }
}