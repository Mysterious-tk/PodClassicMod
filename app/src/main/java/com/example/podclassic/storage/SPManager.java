package com.example.podclassic.storage;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.example.podclassic.base.BaseApplication;
import com.example.podclassic.object.Core;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class SPManager {

    public static class NightMode {
        public static final String AUTO_NAME = "自动";
        public static final int AUTO_ID = 2;
        public static final String ENABLE_NAME = "打开";
        public static final int ENABLE_ID = 1;
        public static final String DISABLE_NAME = "关闭";
        public static final int DISABLE_ID = 0;

        public static final int values = 3;

        public static final String SP_NAME = "night_mode";

        public static boolean nightMode(int id) {
            if (AUTO_ID == id) {
                Calendar calendar = Calendar.getInstance();
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                return hour >= 23 || hour <= 5;
            }
            return ENABLE_ID == id;
        }

        public static String getString(int id) {
            switch (id) {
                case ENABLE_ID:
                    return ENABLE_NAME;
                case DISABLE_ID:
                    return DISABLE_NAME;
                case AUTO_ID:
                    return AUTO_NAME;
            }
            return "";
        }
    }

    public static class Sound {
        public static final String BOTH_NAME = "按键音和振动";
        public static final int BOTH_ID = 0b11;
        public static final String SOUND_NAME = "按键音";
        public static final int SOUND_ID = 0b01;
        public static final String DISABLE_NAME = "关闭";
        public static final int DISABLE_ID = 0;
        public static final String VIBRATE_NAME = "振动";
        public static final int VIBRATE_ID = 0b10;

        public static final int values = 4;

        public static final String SP_NAME = "sound";

        public static String getString(int id) {
            switch (id) {
                case VIBRATE_ID:
                    return VIBRATE_NAME;
                case DISABLE_ID:
                    return DISABLE_NAME;
                case SOUND_ID:
                    return SOUND_NAME;
                case BOTH_ID:
                    return BOTH_NAME;
            }
            return "";
        }
    }

    public static class AutoStop {
        public static final String DISABLE_NAME = "关闭";
        public static final int DISABLE_ID = 0;
        public static final String M15_NAME = "15 分钟";
        public static final int M15_ID = 1;
        public static final String M30_NAME = "30 分钟";
        public static final int M30_ID = 2;
        public static final String M60_NAME = "60 分钟";
        public static final int M60_ID = 3;
        public static final String M90_NAME = "90 分钟";
        public static final int M90_ID = 4;
        public static final String M120_NAME = "120 分钟";
        public static final int M120_ID = 5;

        public static final int values = 6;

        public static final String SP_NAME = "auto_stop";

        public static String getString(int id) {
            switch (id) {
                case DISABLE_ID:
                    return DISABLE_NAME;
                case M15_ID:
                    return M15_NAME;
                case M30_ID:
                    return M30_NAME;
                case M60_ID:
                    return M60_NAME;
                case M90_ID:
                    return M90_NAME;
                case M120_ID:
                    return M120_NAME;
            }
            return "";
        }

        public static int getMinute(int id) {
            switch (id) {
                case DISABLE_ID:
                    return 0;
                case M15_ID:
                    return 15;
                case M30_ID:
                    return 30;
                case M60_ID:
                    return 60;
                case M90_ID:
                    return 90;
                case M120_ID:
                    return 120;
            }
            return 0;
        }

    }

    private SPManager() { }

    public static final String SP_STARTED = "started";
    public static final String SP_AUDIO_FOCUS = "audio_focus";
    public static final String SP_THEME = "theme";
    public static final String SP_RESET_COUNT = "reset_count";
    public static final String SP_SHOW_TIME = "show_time";
    public static final String SP_SHOW_LYRIC = "show_lyric";
    public static final String SP_SAVE_FOLDERS = "save_folders";
    public static final String SP_SAVE_SINGERS = "save_singers";
    public static final String SP_SAVE_ALBUMS = "save_albums";
    public static final String SP_EQUALIZER = "equalizer";
    public static final String SP_PLAY_MODE = "play_mode";
    public static final String SP_PLAY_ALL = "play_all";

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
        setBoolean(SP_PLAY_ALL, true);
        setBoolean(SP_AUDIO_FOCUS, true);
        setBoolean(SP_SHOW_TIME, true);
        setBoolean(SP_SHOW_LYRIC, true);
        setBoolean(SP_THEME, true);
        setInt(NightMode.SP_NAME, NightMode.DISABLE_ID);
        setInt(Sound.SP_NAME, Sound.VIBRATE_ID);
        setInt(AutoStop.SP_NAME, AutoStop.DISABLE_ID);
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
        reset_count++;
        setInt(SP_RESET_COUNT, reset_count);
    }
}
