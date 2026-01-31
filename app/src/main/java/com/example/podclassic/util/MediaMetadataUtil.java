package com.example.podclassic.util;


import android.media.MediaMetadataRetriever;
import android.net.Uri;

import com.example.podclassic.base.BaseApplication;
import com.example.podclassic.bean.Lyric;
import com.example.podclassic.bean.Music;
import com.example.podclassic.values.Strings;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;

public class MediaMetadataUtil {
    private MediaMetadataUtil() {
    }
    public static String convertCodeAndGetText(String str_filepath) {

        File file = new File(str_filepath);
        BufferedReader reader;
        String text = "";
        try {

            FileInputStream fis = new FileInputStream(file);
            BufferedInputStream in = new BufferedInputStream(fis);
            in.mark(4);
            byte[] first3bytes = new byte[3];
            in.read(first3bytes);
            in.reset();
            if (first3bytes[0] == (byte) 0xEF && first3bytes[1] == (byte) 0xBB
                    && first3bytes[2] == (byte) 0xBF) {// utf-8

                reader = new BufferedReader(new InputStreamReader(in, "utf-8"));

            } else if (first3bytes[0] == (byte) 0xFF
                    && first3bytes[1] == (byte) 0xFE) {

                reader = new BufferedReader(
                        new InputStreamReader(in, "unicode"));
            } else if (first3bytes[0] == (byte) 0xFE
                    && first3bytes[1] == (byte) 0xFF) {

                reader = new BufferedReader(new InputStreamReader(in,
                        "utf-16be"));
            } else if (first3bytes[0] == (byte) 0xFF
                    && first3bytes[1] == (byte) 0xFF) {

                reader = new BufferedReader(new InputStreamReader(in,
                        "utf-16le"));
            } else {

                reader = new BufferedReader(new InputStreamReader(in, "GBK"));
            }
            StringBuilder stringBuilder = new StringBuilder();
            String str = reader.readLine();

            while (str != null) {
                stringBuilder.append(str);
                str = reader.readLine();

            }
            reader.close();
            text = stringBuilder.toString();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return text;
    }
    public static Lyric getLyric(File file) {
        String path = file.getPath();
        String lyricPath = path.substring(0, path.lastIndexOf('.')) + ".lrc";
        File lyricFile = new File(lyricPath);
        if (lyricFile.exists() && lyricFile.isFile()) {
            {
                String lyric = convertCodeAndGetText(lyricPath);
                return decodeLyric(lyric);
            }
        } else {
            try {
                RandomAccessFile randomAccessFile = new RandomAccessFile(path, "r");
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
                    while (randomAccessFile.getFilePointer() < totalSize * 8L) {
                        randomAccessFile.read(header);
                        String frameId = new String(header, 0, 4);
                        int size = ((header[4] & 0xff) << 24 | (header[5] & 0xff) << 16 | (header[6] & 0xff) << 8 | (header[7] & 0xff));
                        if ("USLT".equalsIgnoreCase(frameId)) {
                            randomAccessFile.read(temp);
                            String charsetName = getCharsetName(temp[0]);
                            byte[] content = new byte[size - temp.length];
                            randomAccessFile.read(content);
                            return decodeLyric(new String(content, charsetName));
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
                                String tag = new String(info);//, StandardCharsets.UTF_8);
                                int index = tag.indexOf('=');
                                String first = tag.substring(0, index);
                                if ("LYRICS".equalsIgnoreCase(first)) {
                                    return decodeLyric(tag.substring(index + 1));
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
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static int getIntForMp3(byte[] bytes) {
        return bytes[0] << 24 | bytes[1] << 16 | bytes[2] << 8 | bytes[3];
    }

    private static int getIntForFlac(byte[] bytes) {
        return (bytes[0] & 0xff) | (bytes[1] & 0xff) << 8 | (bytes[2] & 0xff) << 16 | (bytes[3] & 0xff) << 24;
    }

    private static String getCharsetName(byte encodingInfo) {
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

    public static Music getMediaMetadata(String path) {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(path);
        String title = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        String artist = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        String album = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
        long duration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        mediaMetadataRetriever.release();
        if (title == null) {
            // 从文件名中提取标题
            File file = new File(path);
            String fileName = file.getName();
            if (fileName.contains(".")) {
                title = fileName.substring(0, fileName.lastIndexOf('.'));
            } else {
                title = fileName;
            }
        }
        if (artist == null) {
            artist = Strings.INSTANCE.getEMPTY();
        }
        if (album == null) {
            album = Strings.INSTANCE.getEMPTY();
        }


        return new Music(title, 0L, artist, album, 0L, duration, path, null);
    }

    public static Music getMediaMetadata(Uri uri) {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(BaseApplication.context, uri);
        Music.Builder builder = new Music.Builder();
        builder.setAlbum(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
        builder.setTitle(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
        builder.setArtist(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));

        mediaMetadataRetriever.release();
        return builder.build();
    }

    public static Music getMediaMetadata(File file) {
        return getMediaMetadata(file.getPath());
    }

    public static Lyric getLyric(String path) {
        return getLyric(new File(path));
    }

    private static Lyric decodeLyric(String lyric) {
        ArrayList<Lyric.LyricLine> list = new ArrayList<>();
        String content;
        String time;
        for (int i = 0; i < lyric.length(); i++) {
            if (lyric.charAt(i) == '[') {
                for (int j = i + 1; j < lyric.length(); j++) {
                    if (lyric.charAt(j) == ']') {
                        if ((i + 1) < j) {
                            time = lyric.substring(i + 1, j);
                            for (int k = j + 1; k < lyric.length(); k++) {
                                if (lyric.charAt(k) == '[') {
                                    if (j + 1 < k) {
                                        content = lyric.substring(j + 1, k);
                                        Lyric.LyricLine l = buildLyric(time, content);
                                        if (l != null) {
                                            list.add(l);
                                        }
                                    }
                                    i = k;
                                    break;
                                } else if (k == lyric.length() - 1) {
                                    if (j < k) {
                                        content = lyric.substring(j + 1, k + 1);
                                        Lyric.LyricLine l = buildLyric(time, content);
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
        }
        if (!list.isEmpty()) {
            return new Lyric(list);
        }
        return null;
    }

    private static Lyric.LyricLine buildLyric(String time, String lyric) {
        try {
            int index_1 = time.indexOf(':');
            int index_2 = time.indexOf('.');
            int min = Integer.parseInt(time.substring(0, index_1));
            int sec = Integer.parseInt(time.substring(index_1 + 1, index_2));
            int mil = Integer.parseInt(time.substring(index_2 + 1));
            int t = min * 60 * 1000 + sec * 1000 + mil;
            return new Lyric.LyricLine(t, lyric);
        } catch (Exception ignored) {
            return null;
        }
    }

}
