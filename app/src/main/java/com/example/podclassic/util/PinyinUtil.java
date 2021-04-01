package com.example.podclassic.util;

import com.example.podclassic.R;
import com.example.podclassic.base.BaseApplication;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;

public class PinyinUtil {

    public static void load() {
        pinyin = readPinyin();
    }
    private static HashMap<Character, Character> readPinyin() {
        try (ObjectInputStream in = new ObjectInputStream(BaseApplication.context.getResources().openRawResource(R.raw.pinyin))) {
            Object obj = in.readObject();
            if (obj == null) {
                return new HashMap<>();
            } else {
                return (HashMap) obj;

            }
        } catch (IOException | ClassNotFoundException e) {
            return new HashMap<>();
        }

    }

    private static HashMap<Character, Character> pinyin = readPinyin();

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