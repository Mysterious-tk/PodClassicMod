package com.example.podclassic.util;

public class StringUtil {
    public static String getSuffix(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }
    public static int toInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return 0;
        }
    }

}
