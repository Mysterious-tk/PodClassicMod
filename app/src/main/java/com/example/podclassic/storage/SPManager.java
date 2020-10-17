package com.example.podclassic.storage;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.example.podclassic.base.BaseApplication;
import com.example.podclassic.object.Core;

public class SPManager {
    private SPManager() {}

    public static final String SP_STARTED = "started";
    public static final String SP_AUDIO_FOCUS = "audio_focus";
    public static final String SP_VIBRATE = "vibrate";
    public static final String SP_SOUND = "sound";
    public static final String SP_THEME = "theme";
    public static final String SP_RESET_COUNT = "reset_count";
    public static final String SP_SHOW_TIME = "show_time";
    public static final String SP_SHOW_LYRIC = "show_lyric";
    public static final String SP_SAVE_FOLDERS = "save_folders";
    public static final String SP_SAVE_SINGERS = "save_singers";
    public static final String SP_SAVE_ALBUMS = "save_albums";
    public static final String SP_EQUALIZER = "equalizer";
    public static final String SP_PLAY_MODE = "play_mode";
    public static final String SP_DARK_MODE = "dark_mode";

    private static SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(BaseApplication.getContext());
    }

    public static boolean getBoolean(String sp) {
        return getSharedPreferences().getBoolean(sp, false);
    }

    public static void setBoolean(String sp, boolean value) {
        getSharedPreferences().edit().putBoolean(sp, value).apply();
    }

    public static int getInt(String sp) {
        return getSharedPreferences().getInt(sp, 0);
    }

    public static void setInt(String sp, int value) {
        getSharedPreferences().edit().putInt(sp, value).apply();
    }

    public static long getLong(String sp) {
        return getSharedPreferences().getLong(sp, 0L);
    }

    public static void setLong(String sp, long value) {
        getSharedPreferences().edit().putLong(sp, value).apply();
    }


    public static String getString(String sp) {
        return getSharedPreferences().getString(sp, "");
    }


    public static void setString(String sp, String value) {
        getSharedPreferences().edit().putString(sp, value).apply();
    }

    public static void reset() {
        setBoolean(SP_VIBRATE, true);
        setBoolean(SP_SOUND, true);
        setBoolean(SP_AUDIO_FOCUS, true);
        setBoolean(SP_SHOW_TIME, true);
        setBoolean(SP_SHOW_LYRIC, true);
        setBoolean(SP_THEME, false);
        setBoolean(SP_DARK_MODE, false);
        setInt(SP_PLAY_MODE, 0);
        setInt(SP_EQUALIZER, 0);
        SaveMusicLists.Companion.getSaveAlbums().clear();
        SaveMusicLists.Companion.getSaveFolders().clear();
        SaveMusicLists.Companion.getSaveSingers().clear();
        SaveMusics.Companion.getLoveList().clear();

        setString(SP_SAVE_SINGERS, "");
        setString(SP_SAVE_ALBUMS, "");
        SaveMusics.Companion.getLoveList().clear();
        int reset_count = getInt(SP_RESET_COUNT);
        reset_count ++;
        setInt(SP_RESET_COUNT, reset_count);
    }
}
