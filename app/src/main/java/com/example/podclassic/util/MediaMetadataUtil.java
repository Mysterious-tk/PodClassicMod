package com.example.podclassic.util;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.example.podclassic.object.Music;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;


public class MediaMetadataUtil {
    private MediaMetadataUtil() {}

    public static LyricSet getLyric(Music music) {
        String path = music.getPath();
        File lyricFile = new File(path.substring(0, path.lastIndexOf('.')) + ".lrc");
        if (lyricFile.exists() && lyricFile.isFile()) {
            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(lyricFile))) {
                StringBuilder stringBuilder = new StringBuilder();
                String buffer;
                while ((buffer = bufferedReader.readLine()) != null) {
                    stringBuilder.append(buffer);
                }
                return decodeLyric(stringBuilder.toString(), music);
            } catch (Exception ignored) { }
        } else {
            File file = new File(path);
            try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
                byte[] temp = new byte[4];

                randomAccessFile.read(temp);
                if ("ID3".equals(new String(temp, 0, 3))) {
                    byte[] header = new byte[10];
                    randomAccessFile.read(header, 0, 6);
                    byte hasExtendHeader = (byte) ((header[1] >> 6) & 1);
                    int totalSize = ((header[2] & 0xff) << 21 | (header[3] & 0xff) << 14 | (header[4] & 0xff) << 7 | (header[5] & 0xff)) + header.length + temp.length;

                    if (hasExtendHeader != 0) {
                        randomAccessFile.read(header);
                        int trashSize = getIntForMp3(header) - (header.length - 4);
                        totalSize -= (header[6] << 24 | header[7] << 16 | header[8] << 8 | header[9]);
                        if (trashSize != 0) {
                            randomAccessFile.skipBytes(trashSize);
                        }
                    }
                    while (randomAccessFile.getFilePointer() < totalSize * 8) {
                        randomAccessFile.read(header);
                        String frameId = new String(header, 0, 4);
                        int size = ((header[4] & 0xff) << 24 | (header[5] & 0xff) << 16 | (header[6] & 0xff) << 8 | (header[7] & 0xff));
                        if ("USLT".equalsIgnoreCase(frameId)) {
                            randomAccessFile.read(temp);
                            String charsetName = getCharsetName(temp[0]);
                            byte[] content = new byte[size - temp.length];
                            randomAccessFile.read(content);
                            return decodeLyric(new String(content, charsetName), music);
                        } else {
                            if (size < 1) {
                                return null;
                            }
                            randomAccessFile.skipBytes(size);
                        }
                    }
                    return null;
                } else if ("fLaC".equals(new String(temp))) {
                    while (true) {
                        randomAccessFile.read(temp);
                        byte isLast = (byte) ((temp[0] & 0xff) >> 7);
                        byte blockType = (byte) (temp[0] & 0x7f);
                        int size = ((temp[1] & 0xff) << 16 | (temp[2] & 0xff) << 8 | (temp[3] & 0xff));
                        if (blockType == 4) {
                            randomAccessFile.read(temp);
                            int vendorLength = getIntForFlac(temp);
                            randomAccessFile.skipBytes(vendorLength);
                            randomAccessFile.read(temp);
                            int commentLength = getIntForFlac(temp);
                            for (int i = 0; i < commentLength; i++) {
                                randomAccessFile.read(temp);
                                int length = getIntForFlac(temp);
                                byte[] info = new byte[length];
                                randomAccessFile.read(info);
                                String tag = new String(info, StandardCharsets.UTF_8);
                                int index = tag.indexOf('=');
                                String first = tag.substring(0, index);
                                if ("LYRICS".equalsIgnoreCase(first)) {
                                    return decodeLyric(tag.substring(index + 1), music);
                                }
                            }
                            return null;
                        } else {
                            randomAccessFile.skipBytes(size);
                        }
                        if (isLast == 1) {
                            return null;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static void getMusicInfo(Music music) {
        File file = new File(music.getPath());
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            byte[] temp = new byte[4];
            randomAccessFile.read(temp);

            boolean hasName = false;
            boolean hasSinger = false;
            boolean hasAlbum = false;

            if ("ID3".equals(new String(temp, 0, 3))) {
                byte[] header = new byte[10];
                randomAccessFile.read(header, 0, 6);
                byte hasExtendHeader = (byte) ((header[1] >> 6) & 1);
                int totalSize = ((header[2] & 0xff) << 21 | (header[3] & 0xff) << 14 | (header[4] & 0xff) << 7 | (header[5] & 0xff)) + header.length + temp.length;

                if (hasExtendHeader != 0) {
                    randomAccessFile.read(header);
                    int trashSize = getIntForFlac(header) - (header.length - 4);
                    totalSize -= (header[6] << 24 | header[7] << 16 | header[8] << 8 | header[9]);
                    if (trashSize != 0) {
                        randomAccessFile.skipBytes(trashSize);
                    }
                }
                while (randomAccessFile.getFilePointer() < totalSize * 8) {
                    randomAccessFile.read(header);
                    String frameId = new String(header, 0, 4).toUpperCase();
                    int size = ((header[4] & 0xff) << 24 | (header[5] & 0xff) << 16 | (header[6] & 0xff) << 8 | (header[7] & 0xff));
                    String charsetName;
                    byte[] content;
                    switch (frameId) {
                        case "TIT2":
                            randomAccessFile.read(temp, 0, 3);
                            charsetName = getCharsetName(temp[0]);
                            content = new byte[size - 3];
                            randomAccessFile.read(content);
                            music.setName(new String(content, charsetName));
                            hasName = true;
                            break;
                        case "TALB":
                            randomAccessFile.read(temp, 0, 3);
                            charsetName = getCharsetName(temp[0]);
                            content = new byte[size - 3];
                            randomAccessFile.read(content);
                            music.setAlbum(new String(content, charsetName));
                            hasAlbum = true;
                            break;
                        case "TPE1": {
                            randomAccessFile.read(temp, 0, 3);
                            charsetName = getCharsetName(temp[0]);
                            content = new byte[size - 3];
                            randomAccessFile.read(content);
                            music.setSinger(new String(content, charsetName));
                            hasSinger = true;
                            break;
                        }
                        default:
                            randomAccessFile.skipBytes(size);
                            break;
                    }
                    if (hasName && hasAlbum && hasSinger) {
                        return;
                    }
                }
            } else if ("fLaC".equals(new String(temp))) {
                while (true) {
                    randomAccessFile.read(temp);
                    byte isLast = (byte) ((temp[0] & 0xff) >> 7);
                    byte blockType = (byte) (temp[0] & 0x7f);
                    int size = ((temp[1] & 0xff) << 16 | (temp[2] & 0xff) << 8 | (temp[3] & 0xff));
                    if (blockType == 4) {
                        randomAccessFile.read(temp);
                        int vendorLength = getIntForFlac(temp);
                        randomAccessFile.skipBytes(vendorLength);
                        randomAccessFile.read(temp);
                        int commentLength = getIntForFlac(temp);
                        for (int i = 0; i < commentLength; i++) {
                            randomAccessFile.read(temp);
                            int length = getIntForFlac(temp);
                            byte[] info = new byte[length];
                            randomAccessFile.read(info);
                            String tag = new String(info, StandardCharsets.UTF_8);
                            int index = tag.indexOf('=');
                            String first = tag.substring(0, index).toUpperCase();
                            switch (first) {
                                case "ALBUM":
                                    music.setAlbum(tag.substring(index + 1));
                                    hasAlbum = true;
                                    break;
                                case "ARTIST":
                                    music.setSinger(tag.substring(index + 1));
                                    hasSinger = true;
                                    break;
                                case "TITLE":
                                    music.setName(tag.substring(index + 1));
                                    hasName = true;
                                    break;
                            }
                            if (hasAlbum && hasName && hasSinger) {
                                return;
                            }
                        }
                        return;
                    } else {
                        randomAccessFile.skipBytes(size);
                    }
                    if (isLast == 1) {
                        return;
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    public static int getIntForMp3(byte[] bytes) {
        return bytes[0] << 24 | bytes[1] << 16 | bytes[2] << 8 | bytes[3];
    }

    public static int getIntForFlac(byte[] bytes) {
        return (bytes[0] & 0xff) | (bytes[1] & 0xff) << 8 | (bytes[2] & 0xff) << 16 | (bytes[3] & 0xff) << 24;
    }

    public static String getCharsetName(byte encodingInfo) {
        String charsetName = "UTF-8";
        switch (encodingInfo) {
            case 0:
                charsetName = "ISO-8859-1";
                break;
            case 1:
                charsetName = "UTF-16LE";
                break;
            case 2:
                charsetName = "UTF-16BE";
                break;
        }
        return charsetName;
    }

    public static class Lyric{
        private int time;
        private String lyric;

        public Lyric(int time, String lyric) {
            this.time = time;
            this.lyric = lyric;
        }
    }

    public static class LyricSet {
        private ArrayList<Lyric> lyrics;
        private int prevIndex = 0;
        public Music music;
        public LyricSet(ArrayList<Lyric> lyrics, Music music) {
            this.lyrics = lyrics;
            this.music = music;
        }

        public String getLyric(int time) {
            if (prevIndex < lyrics.size() - 1 && lyrics.get(prevIndex).time <= time && lyrics.get(prevIndex + 1).time > time) {
                return lyrics.get(prevIndex).lyric;
            } else if (prevIndex < lyrics.size() - 2 && lyrics.get(prevIndex + 1).time <= time && lyrics.get(prevIndex + 2).time > time) {
                return lyrics.get(++prevIndex).lyric;
            }

            int left = 0;
            int right = lyrics.size() - 1;

            // 这里必须是 <=
            while (left <= right) {
                int mid = (left + right) / 2;
                if (lyrics.get(mid).time > time) {
                    right = mid - 1;
                } else {
                    left = mid + 1;
                }
            }
            if (right < 0) {
                right = 0;
            }
            prevIndex = right;
            return lyrics.get(right).lyric;
        }

    }

    private static LyricSet decodeLyric(String lyric, Music music) {
        ArrayList<Lyric> list = new ArrayList<>();
        String content;
        String time;
        for (int i = 0; i < lyric.length(); i++ ) {
            if (lyric.charAt(i) == '[') {
                for (int j = i + 1; j < lyric.length(); j++) {
                    if (lyric.charAt(j) == ']') {
                        time = lyric.substring(i + 1, j);
                        for (int k = j + 1; k < lyric.length(); k++) {
                            if (lyric.charAt(k) == '[') {
                                if (j + 1 != k) {
                                    content = lyric.substring(j + 1, k);
                                    Lyric l = buildLyric(time, content);
                                    if (l != null) {
                                        list.add(l);
                                    }
                                }
                                i = k;
                                break;
                            } else if (k == lyric.length() - 1) {
                                if (j != k) {
                                    content = lyric.substring(j + 1, k + 1);
                                    Lyric l = buildLyric(time, content);
                                    if (l != null) {
                                        list.add(l);
                                    }
                                }
                                i = k + 1;
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (!list.isEmpty()) {
            return new LyricSet(list, music);
        }
        return null;
    }

    private static Lyric buildLyric(String time, String lyric) {
        try {
            int index_1 = time.indexOf(':');
            int index_2 = time.indexOf('.');
            int min = Integer.parseInt(time.substring(0, index_1));
            int sec = Integer.parseInt(time.substring(index_1 + 1, index_2));
            int mil = Integer.parseInt(time.substring(index_2 + 1));
            int t = min * 60 * 1000 + sec * 1000 + mil;
            return new Lyric(t, lyric);
        } catch (Exception ignored) {
            return null;
        }
    }
}
