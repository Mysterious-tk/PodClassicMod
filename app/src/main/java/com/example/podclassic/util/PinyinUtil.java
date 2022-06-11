package com.example.podclassic.util;

import android.content.Context;

import com.example.podclassic.R;
import com.example.podclassic.base.BaseApplication;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;

public class PinyinUtil {
    private static final HashMap<Character, Character> pinyin = getPinyinMap(BaseApplication.context);

    private PinyinUtil() {
    }

    private static HashMap<Character, Character> getPinyinMap(Context context) {
        try (ObjectInputStream in = new ObjectInputStream(context.getResources().openRawResource(R.raw.pinyin))) {
            Object obj = in.readObject();
            in.close();
            if (obj == null) {
                return new HashMap<>();
            } else {
                return (HashMap) obj;
            }


        } catch (IOException | ClassNotFoundException e) {
            return new HashMap<>();
        }

    }

    private static char upper(char c) {
        if (c >= 'a' && c <= 'z') {
            return (char) (c - 'a' + 'A');
        }
        return c;
    }

    private static char lower(char c) {
        if (c >= 'A' && c <= 'Z') {
            return (char) (c - 'A' + 'a');
        }
        return c;
    }

    public static String getPinyin(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            Character c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c = lower(c);
                sb.append(c);
            } else if (c >= 'a' && c <= 'z') {
                sb.append(c);
            } else {
                c = pinyin.get(c);
                if (c != null) {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    public static char getPinyinChar(char c) {
        if (c >= 'a' && c <= 'z') {
            return (char) (c - 32);
        } else if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
            return c;
        }
        Character result = pinyin.get(c);
        return result == null ? '#' : result;
    }
}