package com.example.podclassic.util;


import android.media.MediaMetadataRetriever;

import com.example.podclassic.object.Music;
/*
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.flac.FlacFileReader;
import org.jaudiotagger.audio.generic.AudioFileReader;
import org.jaudiotagger.audio.mp3.MP3FileReader;
import org.jaudiotagger.audio.ogg.OggFileReader;
import org.jaudiotagger.audio.wav.WavFileReader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MediaMetadataUtil {
    private MediaMetadataUtil() {}

    public static ArrayList<Lyric> getLyric(File file) {
        String path = file.getPath();
        File lyricFile = new File(path.substring(0, path.lastIndexOf('.')) + ".lrc");
        if (lyricFile.exists() && lyricFile.isFile()) {
            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(lyricFile))) {
                StringBuilder stringBuilder = new StringBuilder();
                String buffer;
                while ((buffer = bufferedReader.readLine()) != null) {
                    stringBuilder.append(buffer);
                }
                return decodeLyric(stringBuilder.toString());
            } catch (Exception ignored) { }
        } else {
            try  {
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
        mediaMetadataRetriever.release();

        Music music = new Music(title, album, artist, path, 0L);

        return music;
    }

    public static Music getMediaMetadata(File file) {
        return getMediaMetadata(file.getPath());
        /*
        AudioFileReader reader = createFileReader(file.getPath());
        if (reader == null) {
            return null;
        }

        Tag tag = null;
        AudioFile audioFile;
        try {
            audioFile = reader.read(file);
            tag = audioFile.getTag();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Music music = null;

        if (tag == null) {
            music = new Music(file.getName(), null, null, file.getPath(), 0L);
        } else {
            String name = tag.getFirst(FieldKey.TITLE);
            String album = tag.getFirst(FieldKey.ALBUM);
            String singer = tag.getFirst(FieldKey.ARTIST);

            music = new Music(name ,album, singer, file.getPath(), 0L);
        }
         */
    }

    /*
    public static ArrayList<Lyric> getLyric(File file) {
        AudioFileReader reader = createFileReader(file.getPath());
        if (reader == null) {
            return null;
        }

        Tag tag = null;

        try {
            AudioFile audioFile = reader.read(file);
            tag = audioFile.getTag();
        } catch (Exception e) {
            e.printStackTrace();
        }


        if (tag == null) {
            return null;
        }
        String lyric = tag.getFirst(FieldKey.LYRICS);
        if (lyric != null) {
            return decodeLyric(lyric);
        }
        return null;
    }

     */

    public static ArrayList<Lyric> getLyric(String path) {
        return getLyric(new File(path));
    }

    /*

    private static AudioFileReader createFileReader(String path) {
        switch (StringUtil.getSuffix(path).toLowerCase(Locale.ROOT)) {
            case "wav":
                return new WavFileReader();
            case "ogg":
                return new OggFileReader();
            case "mp3":
                return new MP3FileReader();
            case "flac":
                return new FlacFileReader();
        }
        return null;
    }

     */

    public static class Lyric{
        public final int time;
        public final String lyric;

        public Lyric(int time, String lyric) {
            this.time = time;
            this.lyric = lyric;
        }
    }

    private static ArrayList<Lyric> decodeLyric(String lyric) {
        ArrayList<Lyric> list = new ArrayList<>();
        String content;
        String time;
        for (int i = 0; i < lyric.length(); i++ ) {
            if (lyric.charAt(i) == '[') {
                for (int j = i + 1; j < lyric.length(); j++) {
                    if (lyric.charAt(j) == ']') {
                        if((i + 1) < j) {
                            time = lyric.substring(i + 1, j);
                            for (int k = j + 1; k < lyric.length(); k++) {
                                if (lyric.charAt(k) == '[') {
                                    if (j + 1 < k) {
                                        content = lyric.substring(j + 1, k);
                                        Lyric l = buildLyric(time, content);
                                        if (l != null) {
                                            list.add(l);
                                        }
                                    }
                                    i = k;
                                    break;
                                } else if (k == lyric.length() - 1) {
                                    if (j < k) {
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
        }
        if (!list.isEmpty()) {
            return list;
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
