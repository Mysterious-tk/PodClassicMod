package com.example.podclassic.values

import com.example.podclassic.R
import com.example.podclassic.base.BaseApplication

object Strings {
    const val iPod = "iPod"
    const val NULL = ""
    var SONG: String = ""
    var SCORE: String = ""
    var PHOTO: String = ""
    var VIDEO: String = ""
    var EXTRA_APPLICATION: String = ""
    var FILE: String = ""
    var INTERNAL_STORAGE: String = ""
    var SDCARD: String = ""
    var SETTINGS: String = ""
    var SHUFFLE_PLAY: String = ""
    var NOW_PLAYING: String = ""
    var CURRENT_PLAYLIST: String = ""
    var MUSIC: String = ""
    var ALBUM: String = ""
    var CURRENT: String = ""
    var SAVE: String = ""
    var ARTIST: String = ""
    var SAVE_FOLDER: String = ""
    var SAVE_MUSIC: String = ""
    var CORRECT: String = ""
    var INCORRECT: String = ""
    var SAVE_ARTIST: String = ""
    var SAVE_ALBUM: String = ""
    var COVER_FLOW: String = ""
    var ALL: String = ""
    var FOLDER: String = ""
    var EMPTY: String = ""
    var GAME: String = ""
    var BRICK: String = ""
    var MUSIC_QUIZ: String = ""
    var ABOUT: String = ""
    var ABOUT_ME: String = ""
    var CAPACITY: String = ""
    var CAPACITY_AVAILABLE: String = ""
    var VERSION: String = ""
    var SN: String = ""
    var MODEL: String = ""
    var FORMAT: String = ""
    var SLEEP_TIME: String = ""
    var DISABLE: String = ""
    var ENABLE: String = ""
    var MINUTE: String = ""
    var LANGUAGE: String = ""
    var LANGUAGE_EN: String = ""
    var LANGUAGE_CN: String = ""
    var LANGUAGE_TW: String = ""
    var EQUALIZER: String = ""
    var PLAY_MODE: String = ""
    var PLAY_MODE_SINGLE: String = ""
    var PLAY_MODE_SHUFFLE: String = ""
    var PLAY_MODE_ORDER: String = ""
    var APPLICATION: String = ""
    var REPEAT_MODE: String = ""
    var REPEAT_MODE_NONE: String = ""
    var REPEAT_MODE_ALL: String = ""
    var NIGHT_MODE: String = ""
    var PLAY_WITH_OTHER: String = ""
    var TOUCH_FEEDBACK: String = ""
    var VIBRATE: String = ""
    var SOUND: String = ""
    var SOUND_AND_VIBRATE: String = ""
    var THEME: String = ""
    var THEME_RED: String = ""
    var THEME_BLACK: String = ""
    var SHOW_LYRIC: String = ""
    var SHOW_INFO: String = ""
    var AUTO_PLAY: String = ""
    var RESET_ALL_SETTINGS: String = ""
    var RESET_ALL: String = ""
    var CANCEL: String = ""
    var RESET: String = ""
    var SHOW_TIME: String = ""
    var KEEP_PLAYING: String = ""
    var AUTO: String = ""
    var THEME_WHITE: String = ""

    init {
        init()
    }

    fun init() {
        BaseApplication.context.apply {
            SONG = getString(R.string.song)
            THEME_WHITE = getString(R.string.theme_white)
            PHOTO = getString(R.string.photo)
            VIDEO = getString(R.string.video)
            EXTRA_APPLICATION = getString(R.string.extra_application)
            SETTINGS = getString(R.string.settings)
            SHUFFLE_PLAY = getString(R.string.shuffle_play)
            NOW_PLAYING = getString(R.string.now_playing)
            FILE = getString(R.string.file)
            INTERNAL_STORAGE = getString(R.string.internal_storage)
            SDCARD = getString(R.string.sdcard)
            CURRENT_PLAYLIST = getString(R.string.current_playlist)
            MUSIC = getString(R.string.music)
            ALBUM = getString(R.string.album)
            ARTIST = getString(R.string.artist)
            SAVE_FOLDER = getString(R.string.save_folder)
            SAVE_MUSIC = getString(R.string.save_music)
            KEEP_PLAYING = getString(R.string.keep_playing)
            AUTO = getString(R.string.auto)
            SAVE_ARTIST = getString(R.string.save_artist)
            SAVE_ALBUM = getString(R.string.save_album)
            COVER_FLOW = getString(R.string.cover_flow)
            ALL = getString(R.string.all)
            EMPTY = getString(R.string.empty)
            GAME = getString(R.string.game)
            BRICK = getString(R.string.brick)
            MUSIC_QUIZ = getString(R.string.music_quiz)
            ABOUT = getString(R.string.about)
            CAPACITY = getString(R.string.capacity)
            CAPACITY_AVAILABLE = getString(R.string.capacity_available)
            VERSION = getString(R.string.version)
            SN = getString(R.string.sn)
            MODEL = getString(R.string.model)
            FORMAT = getString(R.string.format)
            SLEEP_TIME = getString(R.string.sleep_time)
            DISABLE = getString(R.string.disable)
            APPLICATION = getString(R.string.application)
            SCORE = getString(R.string.score)
            INCORRECT = getString(R.string.incorrect)
            CORRECT = getString(R.string.correct)
            FOLDER = getString(R.string.folder)
            CURRENT = getString(R.string.current)
            SAVE = getString(R.string.save)
            ABOUT_ME = getString(R.string.about_me)

            SHOW_TIME = getString(R.string.show_time)
            ENABLE = getString(R.string.enable)
            MINUTE = getString(R.string.minute)
            LANGUAGE = getString(R.string.language)
            LANGUAGE_EN = getString(R.string.language_en)
            LANGUAGE_CN = getString(R.string.language_cn)
            LANGUAGE_TW = getString(R.string.language_tw)
            EQUALIZER = getString(R.string.equalizer)
            PLAY_MODE = getString(R.string.play_mode)
            PLAY_MODE_SINGLE = getString(R.string.play_mode_single)
            PLAY_MODE_SHUFFLE = getString(R.string.play_mode_shuffle)
            PLAY_MODE_ORDER = getString(R.string.play_mode_order)
            REPEAT_MODE = getString(R.string.repeat_mode)
            REPEAT_MODE_NONE = getString(R.string.repeat_mode_none)
            REPEAT_MODE_ALL = getString(R.string.repeat_mode_all)
            NIGHT_MODE = getString(R.string.night_mode)
            PLAY_WITH_OTHER = getString(R.string.play_with_other)
            TOUCH_FEEDBACK = getString(R.string.touch_feedback)
            VIBRATE = getString(R.string.vibrate)
            SOUND = getString(R.string.sound)
            SOUND_AND_VIBRATE = getString(R.string.sound_and_vibrate)
            THEME = getString(R.string.theme)
            THEME_RED = getString(R.string.theme_red)
            THEME_BLACK = getString(R.string.theme_black)
            SHOW_LYRIC = getString(R.string.show_lyric)
            SHOW_INFO = getString(R.string.show_info)
            AUTO_PLAY = getString(R.string.auto_play)
            RESET_ALL_SETTINGS = getString(R.string.reset_all_settings)
            RESET_ALL = getString(R.string.reset_all)
            CANCEL = getString(R.string.cancel)
            RESET = getString(R.string.reset)
        }
    }

    fun getString(id: Int): String {
        return BaseApplication.context.getString(id)
    }

}